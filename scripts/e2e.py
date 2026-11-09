"""Real HTTP acceptance checks. Run against a running Compose stack; uses only Python stdlib."""
import argparse
import concurrent.futures
import datetime as dt
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', default='http://localhost:8080')
    parser.add_argument('--output', default='evidence/runtime/e2e.json')
    parser.add_argument('--faults', action='store_true', help='Pause and unpause Hotel container to check timeout compensation')
    args = parser.parse_args()
    results = []

    def check(name, condition, details=None):
        results.append({'name': name, 'passed': bool(condition), 'details': details})
        if not condition:
            raise AssertionError(f'{name}: {details}')

    def request(method, path, token=None, body=None):
        headers = {'X-Request-Id': str(uuid.uuid4())}
        if token:
            headers['Authorization'] = f'Bearer {token}'
        data = None if body is None else json.dumps(body).encode('utf-8')
        if data is not None:
            headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(args.base_url + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=25) as response:
                text = response.read()
                return response.status, json.loads(text) if text else None, dict(response.headers)
        except urllib.error.HTTPError as error:
            text = error.read()
            try: payload = json.loads(text) if text else None
            except json.JSONDecodeError: payload = {'message': text.decode(errors='replace')[:500]}
            return error.code, payload, dict(error.headers)

    def until(call, predicate, timeout=90):
        deadline = time.monotonic() + timeout
        while True:
            try:
                result = call()
                if predicate(result): return result
            except (OSError, TimeoutError): pass
            if time.monotonic() > deadline: raise TimeoutError('Service readiness deadline exceeded')
            time.sleep(1)

    try:
        until(lambda: request('POST', '/api/users/auth', body={'username':'student','password':'DemoUser123!'}), lambda result: result[0] == 200)
        status, account, _ = request('POST','/api/users/auth',body={'username':'student','password':'DemoUser123!'})
        check('user_login', status == 200 and account['expiresIn'] == 3600)
        user = account['accessToken']
        _, account2, _ = request('POST','/api/users/auth',body={'username':'student2','password':'DemoUser123!'})
        other = account2['accessToken']
        _, admin_account, _ = request('POST','/api/users/auth',body={'username':'admin','password':'DemoAdmin123!'})
        admin = admin_account['accessToken']
        status, _, headers = request('GET','/api/bookings')
        check('anonymous_401_and_trace', status == 401 and bool(headers.get('X-Request-Id') or headers.get('x-request-id')))
        check('user_cannot_administer', request('GET','/api/users',user)[0] == 403)
        check('internal_release_hidden', request('POST','/api/rooms/1/release',admin,{})[0] == 403)
        suffix = str(uuid.uuid4())[:8]
        status, hotel, _ = request('POST','/api/hotels',admin,{'name':'Acceptance '+suffix,'address':'Test address'})
        check('create_hotel', status == 201)
        hotel_id = hotel['id']
        room_ids = []
        for number in ['101','102','103']:
            status, room, _ = request('POST','/api/rooms',admin,{'hotelId':hotel_id,'number':number,'available':True})
            check('create_room_'+number, status == 201)
            room_ids.append(room['id'])
        start = (dt.datetime.now(dt.timezone.utc).date()+dt.timedelta(days=30)).isoformat()
        end = (dt.datetime.now(dt.timezone.utc).date()+dt.timedelta(days=32)).isoformat()
        query = f'?hotelId={hotel_id}&startDate={start}&endDate={end}'
        until(lambda: request('GET','/api/rooms/recommend'+query,user), lambda result: result[0] == 200)
        status, recommended, _ = request('GET','/api/rooms/recommend'+query,user)
        check('stable_recommendation', status == 200 and [r['id'] for r in recommended] == sorted(room_ids))
        payload = {'requestId':str(uuid.uuid4()),'roomId':room_ids[0],'autoSelect':False,'startDate':start,'endDate':end}
        status, first, _ = request('POST','/api/bookings',user,payload)
        check('confirmed_booking',status == 201 and first['status']=='CONFIRMED')
        status, replay, _ = request('POST','/api/bookings',user,payload)
        check('idempotent_delivery',status == 200 and replay['id']==first['id'])
        check('foreign_booking_is_hidden',request('GET','/api/bookings/'+first['id'],other)[0] == 404)
        changed = dict(payload,roomId=room_ids[1])
        check('request_payload_conflict',request('POST','/api/bookings',user,changed)[0] == 409)
        cancelled = request('DELETE','/api/bookings/'+first['id'],user)
        check('cancel_and_compensate',cancelled[0] == 200 and cancelled[1]['status']=='CANCELLED' and not cancelled[1]['compensationPending'])

        def concurrent_attempt(_):
            body = dict(payload, requestId=str(uuid.uuid4()), roomId=room_ids[0])
            return request('POST','/api/bookings',user,body)
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            attempts = list(pool.map(concurrent_attempt,range(8)))
        statuses = [result[0] for result in attempts]
        check('parallel_single_winner',statuses.count(201)==1 and statuses.count(409)==7,statuses)
        winner = next(result[1] for result in attempts if result[0]==201)
        request('DELETE','/api/bookings/'+winner['id'],user)

        assigned = []
        for _ in range(3):
            body = dict(payload,requestId=str(uuid.uuid4()),autoSelect=True,hotelId=hotel_id,roomId=999999)
            status, booking, _ = request('POST','/api/bookings',user,body)
            check('auto_select_'+str(len(assigned)+1),status==201 and booking['status']=='CONFIRMED')
            assigned.append(booking['roomId'])
        check('auto_uses_all_free_rooms',set(assigned)==set(room_ids),assigned)
        status, stats, _ = request('GET',f'/api/hotels/{hotel_id}/occupancy?startDate={start}&endDate={end}',admin)
        check('full_occupancy_statistics',status==200 and stats['occupancyPercent']==100.0 and stats['occupiedNights']==6)

        if args.faults:
            fault_payload = dict(payload,requestId=str(uuid.uuid4()),startDate=(dt.date.fromisoformat(end)+dt.timedelta(days=1)).isoformat(),endDate=(dt.date.fromisoformat(end)+dt.timedelta(days=3)).isoformat())
            subprocess.run(['docker','compose','pause','hotel-service'],check=True)
            try:
                status, _, _ = request('POST','/api/bookings',user,fault_payload)
                check('hotel_timeout_returns503',status==503,status)
                _, history, _ = request('GET','/api/bookings?size=100',user)
                failed = next(b for b in history['content'] if b['requestId']==fault_payload['requestId'])
                check('timeout_cancels_booking',failed['status']=='CANCELLED')
                check('compensation_outbox_is_pending',failed['compensationPending'])
            finally:
                subprocess.run(['docker','compose','unpause','hotel-service'],check=True)
            recovered = until(lambda:request('GET','/api/bookings/'+failed['id'],user),lambda result:result[0]==200 and not result[1]['compensationPending'],timeout=45)
            check('background_compensation_finishes',recovered[1]['status']=='CANCELLED')
            time.sleep(2)
            q = f"?hotelId={hotel_id}&startDate={fault_payload['startDate']}&endDate={fault_payload['endDate']}"
            status, free, _ = request('GET','/api/rooms'+q,user)
            check('no_orphan_slot_after_timeout',status==200 and room_ids[0] in [r['id'] for r in free])
        print(f'PASS: {len(results)} HTTP acceptance checks')
    finally:
        output = Path(args.output)
        output.parent.mkdir(parents=True,exist_ok=True)
        output.write_text(json.dumps({'executedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'baseUrl':args.base_url,'faults':args.faults,'checks':results},ensure_ascii=False,indent=2),encoding='utf-8')


if __name__=='__main__':
    main()

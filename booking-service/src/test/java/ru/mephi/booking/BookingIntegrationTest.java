package ru.mephi.booking;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.mephi.booking.api.BookingDtos.*;
import ru.mephi.booking.api.UserDtos;
import ru.mephi.booking.domain.*;
import ru.mephi.booking.domain.AppUser.Role;
import ru.mephi.booking.domain.Booking.Status;
import ru.mephi.booking.integration.*;
import ru.mephi.booking.repository.*;
import ru.mephi.booking.security.TokenService;
import ru.mephi.booking.service.*;
import ru.mephi.shared.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "eureka.client.enabled=false", "app.seed=false", "saga.recovery-delay-ms=3600000"
})
@AutoConfigureMockMvc
class BookingIntegrationTest {
    @Autowired BookingCoordinator coordinator;
    @Autowired BookingTransactions transactions;
    @Autowired BookingRepository bookings;
    @Autowired UserRepository users;
    @Autowired AccountService accounts;
    @Autowired TokenService tokens;
    @Autowired MockMvc mvc;
    @MockitoBean HotelPort hotel;
    AppUser user;
    AppUser another;
    LocalDate start = LocalDate.now(ZoneOffset.UTC).plusDays(10);
    @BeforeEach void setUp() {
        reset(hotel);
        bookings.deleteAll();
        users.deleteAll();
        user = users.save(new AppUser("student", "unused-in-business-tests", Role.USER));
        another = users.save(new AppUser("other", "unused-in-business-tests", Role.USER));
    }
    CreateBooking input(UUID requestId) {
        return new CreateBooking(requestId, 1L, null, false, start, start.plusDays(2));
    }
    CreateBooking input() {
        return input(UUID.randomUUID());
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor as(AppUser who) {
        return jwt().jwt(j -> j.subject(who.getId().toString())).authorities(() -> "ROLE_"+who.getRole());
    }
    String json(UUID requestId) {
        return "{\"requestId\":\""+requestId+"\",\"roomId\":1,\"autoSelect\":false,\"startDate\":\""+start+"\",\"endDate\":\""+start.plusDays(2)+"\"}";
    }
    @Test void pendingIsCommittedBeforeRemoteCallThenConfirmed() {
        doAnswer(call -> {
            BookingView view = call.getArgument(0);
            assertThat(bookings.findById(view.id()).orElseThrow().getStatus()).isEqualTo(Status.PENDING);
            return null;
        }).
        when(hotel).confirm(any());
        var result = coordinator.create(user.getId(), input(), "token");
        assertThat(result.booking().status()).isEqualTo(Status.CONFIRMED);
        verify(hotel, never()).release(any());
    }
    @Test void remoteErrorCancelsAndReleasesSlot() {
        doThrow(new RemoteFailure("503", null)).when(hotel).confirm(any());
        assertThatThrownBy(() -> coordinator.create(user.getId(), input(), "token")).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(503));
        var booking = bookings.findAll().get(0);
        assertThat(booking.getStatus()).isEqualTo(Status.CANCELLED);
        assertThat(booking.isReleaseRequired()).isFalse();
        verify(hotel).release(any());
    }
    @Test void timeoutWithLostResponseIsCompensated() {
        doThrow(new RemoteFailure("Read timeout", new java.net.http.HttpTimeoutException("timeout"))).when(hotel).confirm(any());
        assertThatThrownBy(() -> coordinator.create(user.getId(), input(), "token")).isInstanceOf(DomainException.class);
        verify(hotel).release(any());
        assertThat(bookings.findAll().get(0).getStatus()).isEqualTo(Status.CANCELLED);
    }
    @Test void failedReleaseRemainsInOutboxAndCanBeRetried() {
        doThrow(new RemoteFailure("unavailable", null)).when(hotel).confirm(any());
        doThrow(new RemoteFailure("release unavailable", null)).when(hotel).release(any());
        assertThatThrownBy(() -> coordinator.create(user.getId(), input(), "token")).isInstanceOf(DomainException.class);
        UUID id = bookings.findAll().get(0).getId();
        assertThat(transactions.get(id).compensationPending()).isTrue();
        doNothing().when(hotel).release(any());
        coordinator.compensate(transactions.get(id));
        assertThat(transactions.get(id).compensationPending()).isFalse();
    }
    @Test void repeatRequestReturnsSameBookingAndDoesNotCallHotelAgain() {
        var request = input();
        var first = coordinator.create(user.getId(), request, "token");
        var second = coordinator.create(user.getId(), request, "token");
        assertThat(second.created()).isFalse();
        assertThat(second.booking().id()).isEqualTo(first.booking().id());
        assertThat(bookings.count()).isEqualTo(1);
        verify(hotel, times(1)).confirm(any());
    }
    @Test void parallelDeliveryHasOneOwnerOfSaga() throws Exception {
        var request = input();
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Creation>> jobs = new ArrayList<>();
            for (int i=0;i<16;i++) jobs.add(() -> coordinator.create(user.getId(), request, "token"));
            Set<UUID> ids = new HashSet<>();
            int newlyCreated = 0;
            for (var future : pool.invokeAll(jobs)) {
                var result = future.get(20, TimeUnit.SECONDS);
                ids.add(result.booking().id());
                if(result.created()) newlyCreated++;
            }
            assertThat(ids).hasSize(1);
            assertThat(newlyCreated).isEqualTo(1);
            assertThat(bookings.count()).isEqualTo(1);
            verify(hotel).confirm(any());
        } finally
        {
            pool.shutdownNow();
        }
    }
    @Test void conflictingPayloadUnderSameRequestIdIsRejected() {
        var request = input();
        coordinator.create(user.getId(), request, "token");
        var changed = new CreateBooking(request.requestId(), 2L, null, false, start, start.plusDays(2));
        assertThatThrownBy(() -> coordinator.create(user.getId(), changed, "token"))
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(409));
    }
    @Test void sameRequestIdForDifferentUsersIsIndependent() {
        UUID requestId = UUID.randomUUID();
        coordinator.create(user.getId(), input(requestId), "token");
        coordinator.create(another.getId(), input(requestId), "token");
        assertThat(bookings.count()).isEqualTo(2);
    }
    @Test void cancelledRequestDoesNotCreateNewBookingOnReplay() {
        var request = input();
        var result = coordinator.create(user.getId(), request, "token");
        coordinator.cancel(result.booking().id(), user.getId());
        assertThat(coordinator.create(user.getId(), request, "token").booking().status()).isEqualTo(Status.CANCELLED);
        verify(hotel).confirm(any());
        verify(hotel).release(any());
    }
    @Test void autoSelectionIgnoresRoomIdAndTriesNextCandidateOnConflict() {
        when(hotel.recommend(any(), any(), any(), anyString())).thenReturn(List.of(new RoomView(1L,1L,"101",true,0),new RoomView(2L,1L,"102",true,0)));
        doAnswer(call -> {
            if (((BookingView)call.getArgument(0)).roomId() == 1L) throw new DomainException(409,"ROOM_BUSY","busy");
            return null;
        }).
        when(hotel).confirm(any());
        var request = new CreateBooking(UUID.randomUUID(), 999L, 1L, true, start, start.plusDays(2));
        assertThat(coordinator.create(user.getId(), request, "token").booking().roomId()).isEqualTo(2L);
    }
    @Test void uncertainAutoSelectionDoesNotTryAnotherRoom() {
        when(hotel.recommend(any(), any(), any(), anyString())).thenReturn(List.of(new RoomView(1L,1L,"101",true,0),new RoomView(2L,1L,"102",true,0)));
        doThrow(new RemoteFailure("timeout",null)).when(hotel).confirm(any());
        var request = new CreateBooking(UUID.randomUUID(), null, 1L, true, start, start.plusDays(2));
        assertThatThrownBy(() -> coordinator.create(user.getId(), request, "token")).isInstanceOf(DomainException.class);
        verify(hotel, times(1)).confirm(any());
        verify(hotel).release(argThat(b -> b.roomId() == 1L));
    }
    @Test void cancellationDuringConfirmationCannotResurrectBooking() {
        doAnswer(call -> {
            BookingView view = call.getArgument(0);
            coordinator.cancel(view.id(), user.getId());
            return null;
        }).
        when(hotel).confirm(any());
        var result = coordinator.create(user.getId(), input(), "token");
        assertThat(result.booking().status()).isEqualTo(Status.CANCELLED);
        verify(hotel).release(any());
    }
    @Test void abandonedPendingCanBeCancelledByRecovery() {
        var created = transactions.pending(user.getId(), input());
        transactions.selectRoom(created.booking().id(),1L);
        assertThat(transactions.recoveryCandidates(Instant.now().plusSeconds(1))).contains(created.booking().id());
        coordinator.compensate(transactions.failPending(created.booking().id(), "SAGA_EXPIRED"));
        assertThat(transactions.get(created.booking().id()).status()).isEqualTo(Status.CANCELLED);
        verify(hotel).release(any());
    }
    @Test void userCannotReadOrCancelAnotherUsersBooking() throws Exception {
        UUID id = coordinator.create(user.getId(), input(), "token").booking().id();
        mvc.perform(get("/api/bookings/"+id).with(as(another))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/bookings/"+id).with(as(another))).andExpect(status().isNotFound());
        assertThat(transactions.get(id).status()).isEqualTo(Status.CONFIRMED);
    }
    @Test void historyIsPagedAndFilteredByOwner() throws Exception {
        coordinator.create(user.getId(), input(), "token");
        coordinator.create(another.getId(), input(), "token");
        mvc.perform(get("/api/bookings?page=0&size=1").with(as(user))).andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/bookings?size=101").with(as(user))).andExpect(status().isBadRequest());
    }
    @Test void missingJwtReturns401() throws Exception {
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
    }
    @Test void userCannotAdministerUsers() throws Exception {
        mvc.perform(get("/api/users").with(as(user))).andExpect(status().isForbidden());
    }
    @Test void invalidDatesDoNotCallHotel() throws Exception {
        String invalid = json(UUID.randomUUID()).replace(start.plusDays(2).toString(), start.toString());
        mvc.perform(post("/api/bookings").with(as(user)).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        verifyNoInteractions(hotel);
    }
    @Test void createAndReplayUse201And200() throws Exception {
        String body = json(UUID.randomUUID());
        mvc.perform(post("/api/bookings").with(as(user)).contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(post("/api/bookings").with(as(user)).contentType("application/json").content(body)).andExpect(status().isOk());
    }
    @Test void deletedAccountCannotUseUnexpiredJwt() throws Exception {
        String token = tokens.user(user);
        accounts.delete(user.getId());
        mvc.perform(get("/api/bookings").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void registerIssuesRealJwtAndPasswordNeverLeaks() throws Exception {
        mvc.perform(post("/api/users/register").contentType("application/json")
        .content("{\"username\":\"registered\",\"password\":\"TestPassword123!\"}"))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.accessToken").isString())
        .andExpect(jsonPath("$.expiresIn").value(3600)).andExpect(jsonPath("$.user.password").doesNotExist());
        var account = users.findByUsername("registered").orElseThrow();
        assertThat(account.getPassword()).startsWith("$2").isNotEqualTo("TestPassword123!");
        mvc.perform(get("/api/bookings").header("Authorization","Bearer "+tokens.user(account))).andExpect(status().isOk());
    }
    @Test void wrongPasswordIs401() throws Exception {
        accounts.register(new UserDtos.Credentials("authuser", "CorrectPassword123!"));
        mvc.perform(post("/api/users/auth").contentType("application/json")
        .content("{\"username\":\"authuser\",\"password\":\"WrongPassword123!\"}")).andExpect(status().isUnauthorized());
    }
    @Test void internalTokenIsRejectedByPublicApi() throws Exception {
        mvc.perform(get("/api/bookings").header("Authorization","Bearer "+tokens.internal())).andExpect(status().isUnauthorized());
    }
    @Test void modifiedSignatureIsRejected() throws Exception {
        String token = tokens.user(user);
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        mvc.perform(get("/api/bookings").header("Authorization","Bearer "+String.join(".", parts))).andExpect(status().isUnauthorized());
    }
}

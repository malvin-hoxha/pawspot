package com.pawspot.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.pawspot.message.repositories.ConversationRepository;
import com.pawspot.message.repositories.MessageRepository;
import com.pawspot.message.response.ConversationResponse;
import com.pawspot.message.service.ConversationService;
import com.pawspot.post.PetPost;
import com.pawspot.post.PetPostRepository;
import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;
import com.pawspot.support.PostgresIntegrationTest;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.pawspot.image.CloudinaryService;
import com.pawspot.post.PetPostService;
import com.pawspot.user.UserService;


@SpringBootTest
@AutoConfigureMockMvc
public class ConversationIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private PetPostRepository petPostRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PetPostService petPostService;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @Test
    void shouldReturnSameConversationForConcurrentStarts() throws Exception {
        User owner = new User();
        owner.setName("Post Owner");
        owner.setEmail("conversation-owner@test.com");
        owner.setPassword("test-password");
        User savedOwner = userRepository.save(owner);

        User interested = new User();
        interested.setName("Interested User");
        interested.setEmail("conversation-interested@test.com");
        interested.setPassword("test-password");
        User savedInterested = userRepository.save(interested);

        PetPost post = new PetPost();
        post.setTitle("Lost dog");
        post.setDescription("Small brown dog");
        post.setCity("Thessaloniki");
        post.setType(PostType.LOST);
        post.setSpecies(PetSpecies.DOG);
        post.setStatus(PostStatus.ACTIVE);
        post.setUser(savedOwner);

        Long postId = petPostRepository.saveAndFlush(post).getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<ConversationResponse> startTask = () -> {
            ready.countDown();

            assertTrue(
                start.await(5, TimeUnit.SECONDS),
                "Start signal was not received"
            );

            return conversationService.startConversation(postId, savedInterested);
        };

        try {
            Future<ConversationResponse> first = executor.submit(startTask);
            Future<ConversationResponse> second = executor.submit(startTask);

            // Hold the post lock until both service calls are waiting for it.
            transactionTemplate.executeWithoutResult(status -> {
                petPostRepository.findByIdForUpdate(postId).orElseThrow();

                try {
                    assertTrue(
                        ready.await(5, TimeUnit.SECONDS),
                        "Conversation workers were not ready"
                    );
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }

                start.countDown();
                awaitBlockedDatabaseTransactions(2);
            });

            // The holding transaction has committed; both calls can now finish.
            ConversationResponse firstResponse = first.get(15, TimeUnit.SECONDS);
            ConversationResponse secondResponse = second.get(15, TimeUnit.SECONDS);

            assertEquals(firstResponse.id(), secondResponse.id());
            assertEquals(1L, conversationRepository.count());

            Conversation stored = conversationRepository
                .findByPostIdAndInterestedUserId(postId, savedInterested.getId())
                .orElseThrow();

            assertEquals(stored.getId(), firstResponse.id());
            assertEquals(savedOwner.getId(), firstResponse.otherUserId());
            assertEquals(savedOwner.getId(), secondResponse.otherUserId());
        } finally {
            // Release waiting workers even if the test fails before starting them.
            start.countDown();
            executor.shutdownNow();

            assertTrue(
                executor.awaitTermination(5, TimeUnit.SECONDS),
                "Executor did not terminate within 5 seconds"
            );
        }
    }

    @Test
    void shouldAllowBothParticipantsToSendMessages() {
        User owner = createUser("Owner", "owner@test.com");
        User interested = createUser("Interested", "interested@test.com");
        PetPost post = createPost(owner);

        var conversation = conversationService.startConversation(
            post.getId(),
            interested
        );

        var first = conversationService.sendMessage(
            conversation.id(),
            interested,
            "  Το είδα στο πάρκο  "
        );

        var second = conversationService.sendMessage(
            conversation.id(),
            owner,
            "Ευχαριστώ!"
        );

        assertEquals(interested.getId(), first.senderId());
        assertEquals(owner.getId(), second.senderId());
        assertEquals("Το είδα στο πάρκο", first.body());
        assertNotNull(first.createdAt());
        assertEquals(2L, messageRepository.count());

        // Verify the stored message, not only the response.
        Message stored = messageRepository.findById(first.id()).orElseThrow();

        assertEquals("Το είδα στο πάρκο", stored.getBody());
        assertEquals(conversation.id(), stored.getConversation().getId());
        assertEquals(interested.getId(), stored.getSender().getId());
    }

    @Test
    void shouldRejectMessageFromNonParticipant() {
        User owner = createUser("Owner", "owner@test.com");
        User interested = createUser("Interested", "interested@test.com");
        User stranger = createUser("Stranger", "stranger@test.com");
        PetPost post = createPost(owner);

        var conversation = conversationService.startConversation(
            post.getId(),
            interested
        );

        ResponseStatusException error = assertThrows(
            ResponseStatusException.class,
            () -> conversationService.sendMessage(
                conversation.id(),
                stranger,
                "Unauthorized message"
            )
        );

        assertEquals(404, error.getStatusCode().value());
        assertEquals(0L, messageRepository.count());
    }

    @Test
    void shouldRejectInvalidMessageBodies() {
        User owner = createUser("Owner", "owner@test.com");
        User interested = createUser("Interested", "interested@test.com");
        PetPost post = createPost(owner);

        var conversation = conversationService.startConversation(
            post.getId(),
            interested
        );

        String[] invalidBodies = {
            null,
            "",
            " \t\n ",
            "a".repeat(2001)
        };

        for (String body : invalidBodies) {
            ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> conversationService.sendMessage(
                    conversation.id(),
                    interested,
                    body
                )
            );

            assertEquals(400, error.getStatusCode().value());
        }

        assertEquals(0L, messageRepository.count());

        // The exact maximum length must still be accepted.
        var accepted = conversationService.sendMessage(
            conversation.id(),
            interested,
            "a".repeat(2000)
        );

        assertEquals(2000, accepted.body().length());
        assertEquals(1L, messageRepository.count());
    }

    private User createUser(String name, String email) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("test-password");
        return userRepository.save(user);
    }

    private PetPost createPost(User owner) {
        PetPost post = new PetPost();
        post.setTitle("Lost dog");
        post.setDescription("Small brown dog");
        post.setCity("Thessaloniki");
        post.setType(PostType.LOST);
        post.setSpecies(PetSpecies.DOG);
        post.setStatus(PostStatus.ACTIVE);
        post.setUser(owner);
        return petPostRepository.saveAndFlush(post);
    }

    @Test
    void shouldRequireAuthenticationToReadConversations() throws Exception {
        mockMvc.perform(get("/api/conversations/1"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/conversations/1/messages"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/conversations"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidMessagePagination() throws Exception {
        User owner = createUser("Owner", "owner@test.com");
        User interested = createUser("Interested", "interested@test.com");
        PetPost post = createPost(owner);

        var conversation = conversationService.startConversation(
            post.getId(),
            interested
        );

        var auth = new UsernamePasswordAuthenticationToken(
            interested,
            null,
            List.of()
        );

        int[][] invalidPagination = {
            {-1, 50},
            {0, 0},
            {0, 101}
        };

        for (int[] parameters : invalidPagination) {
            mockMvc.perform(
                get("/api/conversations/{id}/messages", conversation.id())
                    .with(authentication(auth))
                    .param("page", String.valueOf(parameters[0]))
                    .param("size", String.valueOf(parameters[1]))
            )
            .andExpect(status().isBadRequest());
        }
    }

    @Test
    void shouldSendAndReadMessagesThroughEndpoints() throws Exception {
        User owner = createUser("Owner", "owner@test.com");
        User interested = createUser("Interested", "interested@test.com");
        PetPost post = createPost(owner);

        var conversation = conversationService.startConversation(
            post.getId(), interested
        );

        var interestedAuth = new UsernamePasswordAuthenticationToken(
            interested, null, List.of()
        );

        var ownerAuth = new UsernamePasswordAuthenticationToken(
            owner, null, List.of()
        );

        mockMvc.perform(
            post("/api/conversations/{id}/messages", conversation.id())
                .with(authentication(interestedAuth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"body": "Το είδα στο πάρκο"}
                    """)
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.senderId").value(interested.getId()))
        .andExpect(jsonPath("$.body").value("Το είδα στο πάρκο"));

        // The owner should see the interested user as the other participant.
        mockMvc.perform(
            get("/api/conversations/{id}", conversation.id())
                .with(authentication(ownerAuth))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.otherUserId").value(interested.getId()));

        mockMvc.perform(
            get("/api/conversations/{id}/messages", conversation.id())
                .with(authentication(ownerAuth))
                .param("page", "0")
                .param("size", "1")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].body").value("Το είδα στο πάρκο"))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldRejectNonParticipantThroughEndpoints() throws Exception {
        User owner = createUser("Owner", "owner@test.com");
        User interested = createUser("Interested", "interested@test.com");
        User stranger = createUser("Stranger", "stranger@test.com");
        PetPost post = createPost(owner);

        var conversation = conversationService.startConversation(
            post.getId(), interested
        );

        var strangerAuth = new UsernamePasswordAuthenticationToken(
            stranger, null, List.of()
        );

        mockMvc.perform(
            get("/api/conversations/{id}", conversation.id())
                .with(authentication(strangerAuth))
        )
        .andExpect(status().isNotFound());

        mockMvc.perform(
            get("/api/conversations/{id}/messages", conversation.id())
                .with(authentication(strangerAuth))
        )
        .andExpect(status().isNotFound());

        mockMvc.perform(
            post("/api/conversations/{id}/messages", conversation.id())
                .with(authentication(strangerAuth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"body": "Unauthorized message"}
                    """)
        )
        .andExpect(status().isNotFound());

        assertEquals(0L, messageRepository.count());
    }

    @Test
    void shouldStartConversationOnceThroughEndpoint() throws Exception {
        User owner = createUser("Owner", "owner@test.com");
        User interested = createUser("Interested", "interested@test.com");
        PetPost post = createPost(owner);

        var auth = new UsernamePasswordAuthenticationToken(
            interested, null, List.of()
        );

        var firstResult = mockMvc.perform(
            post("/api/posts/{id}/conversations", post.getId())
                .with(authentication(auth))
                .with(csrf())
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.postId").value(post.getId()))
        .andExpect(jsonPath("$.otherUserId").value(owner.getId()))
        .andReturn();

        Conversation stored = conversationRepository
            .findByPostIdAndInterestedUserId(post.getId(), interested.getId())
            .orElseThrow();

        jsonPath("$.id").value(stored.getId()).match(firstResult);

        mockMvc.perform(
            post("/api/posts/{id}/conversations", post.getId())
                .with(authentication(auth))
                .with(csrf())
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(stored.getId()));

        assertEquals(1L, conversationRepository.count());
    }

    @Test
    void shouldListOnlyCurrentUsersConversationsWithPagination() throws Exception {
        User viewer = createUser("Viewer", "viewer@test.com");
        User owner = createUser("Owner", "owner@test.com");
        User interested = createUser("Interested", "interested@test.com");
        User stranger = createUser("Stranger", "stranger@test.com");

        PetPost viewersPost = createPost(viewer);
        PetPost ownersPost = createPost(owner);

        // The viewer participates as the post owner.
        var firstConversation = conversationService.startConversation(
            viewersPost.getId(), interested
        );

        // The viewer participates as the interested user.
        var secondConversation = conversationService.startConversation(
            ownersPost.getId(), viewer
        );

        // This conversation must not appear in the viewer's inbox.
        conversationService.startConversation(ownersPost.getId(), interested);

        var viewerAuth = new UsernamePasswordAuthenticationToken(
            viewer, null, List.of()
        );

        mockMvc.perform(
            get("/api/conversations")
                .with(authentication(viewerAuth))
                .param("page", "0")
                .param("size", "1")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(secondConversation.id()))
        .andExpect(jsonPath("$.content[0].otherUserId").value(owner.getId()))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(
            get("/api/conversations")
                .with(authentication(viewerAuth))
                .param("page", "1")
                .param("size", "1")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(firstConversation.id()))
        .andExpect(jsonPath("$.content[0].otherUserId").value(interested.getId()))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.totalElements").value(2));

        var strangerAuth = new UsernamePasswordAuthenticationToken(
            stranger, null, List.of()
        );

        mockMvc.perform(
            get("/api/conversations")
                .with(authentication(strangerAuth))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldDeleteConversationsAndMessagesWhenPostIsDeleted() {
        User owner = createUser("Owner", "owner@test.com");
        User interested = createUser("Interested", "interested@test.com");
        PetPost post = createPost(owner);

        var conversation = conversationService.startConversation(
            post.getId(), interested
        );
        var message = conversationService.sendMessage(
            conversation.id(), interested, "I saw your dog."
        );

        petPostService.deletePost(post.getId(), owner);

        assertFalse(petPostRepository.existsById(post.getId()));
        assertFalse(conversationRepository.existsById(conversation.id()));
        assertFalse(messageRepository.existsById(message.id()));
    }

    @Test
    void shouldDeleteOnlyConversationsInvolvingDeletedAccount() {
        String password = "test-password";

        User account = createUser("Account", "account@test.com");
        account.setPassword(passwordEncoder.encode(password));
        account = userRepository.saveAndFlush(account);

        User owner = createUser("Owner", "owner@test.com");
        User interested = createUser("Interested", "interested@test.com");

        PetPost accountsPost = createPost(account);
        PetPost ownersPost = createPost(owner);

        // Ο λογαριασμός που διαγράφεται είναι ο δημιουργός της αγγελίας.
        var ownedConversation = conversationService.startConversation(
            accountsPost.getId(), interested
        );
        var ownedMessage = conversationService.sendMessage(
            ownedConversation.id(), interested, "Is the dog still missing?"
        );

        // Ο ίδιος λογαριασμός συμμετέχει ως ενδιαφερόμενος σε ξένη αγγελία.
        var joinedConversation = conversationService.startConversation(
            ownersPost.getId(), account
        );
        var joinedMessage = conversationService.sendMessage(
            joinedConversation.id(), account, "I might have seen the dog."
        );

        // Μια άσχετη συζήτηση στην ίδια ξένη αγγελία πρέπει να παραμείνει.
        var unrelatedConversation = conversationService.startConversation(
            ownersPost.getId(), interested
        );
        var unrelatedMessage = conversationService.sendMessage(
            unrelatedConversation.id(), owner, "Thanks for reaching out."
        );

        userService.deleteAccount(account.getId(), password);

        assertFalse(userRepository.existsById(account.getId()));
        assertFalse(petPostRepository.existsById(accountsPost.getId()));
        assertFalse(conversationRepository.existsById(ownedConversation.id()));
        assertFalse(conversationRepository.existsById(joinedConversation.id()));
        assertFalse(messageRepository.existsById(ownedMessage.id()));
        assertFalse(messageRepository.existsById(joinedMessage.id()));

        assertTrue(petPostRepository.existsById(ownersPost.getId()));
        assertTrue(conversationRepository.existsById(unrelatedConversation.id()));
        assertTrue(messageRepository.existsById(unrelatedMessage.id()));
    }

}

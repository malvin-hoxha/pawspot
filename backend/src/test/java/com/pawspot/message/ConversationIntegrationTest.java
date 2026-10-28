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


@SpringBootTest
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
}

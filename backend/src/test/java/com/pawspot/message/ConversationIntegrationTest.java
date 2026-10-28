package com.pawspot.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.pawspot.message.repositories.ConversationRepository;
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
}

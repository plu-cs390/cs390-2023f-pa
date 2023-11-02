package poi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import poi.bootstrap.PoiBootstrap;
import poi.model.Poi;
import poi.model.Review;
import poi.repo.PoiRepository;
import poi.repo.ReviewRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ReviewTests {
    @LocalServerPort
    private int port;

    @Autowired
    private ReviewRepository repo;

    @Autowired
    private PoiRepository poiRepo;

    @Autowired
    private PoiBootstrap bootstrap;

    private HttpClient client = HttpClient.newHttpClient();

    private Poi poi1;
    private List<Review> reviews;

    @BeforeEach
    public void setUp() {
        // Start with a clean database
        repo.deleteAll();
        poiRepo.deleteAll();

        // Load seed data
        bootstrap.onApplicationStart(null);

        // Get the first Poi in the database
        poi1 = poiRepo.findAll(Pageable.ofSize(1)).toList().get(0);

        // Create two reviews
        reviews = List.of(
                new Review(1, "A", poi1.getId()),
                new Review(4, "B", poi1.getId())
        );
        reviews = repo.saveAll(reviews);
    }

    @Test
    public void createReview() throws Exception {
        Review review = new Review(3, "A", poi1.getId());
        ObjectMapper mapper = new ObjectMapper();
        String jsonBody = mapper.writeValueAsString(review);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/poi/" + poi1.getId() + "/reviews"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody) )
                .build();
        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString() );

        assertEquals(201, response.statusCode());

        Review poiResponse = mapper.readValue(response.body(), Review.class);

        // Get the review from the database
        Review reviewFromDb = repo.findById(poiResponse.getId()).get();

        // Validate that the DB object is the same as the original Review
        assertEquals(review.getStars(), reviewFromDb.getStars());
        assertEquals(review.getReview(), reviewFromDb.getReview());
        assertEquals(review.getPoiId(), reviewFromDb.getPoiId());
    }

    // Client should not be allowed to specify the ID of a new review
    @Test
    public void createReviewShouldUseAutoGeneratedId() throws Exception {
        Review review = new Review(3, "A", poi1.getId());
        review.setId("aaaa");
        ObjectMapper mapper = new ObjectMapper();
        String jsonBody = mapper.writeValueAsString(review);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/poi/" + poi1.getId() + "/reviews"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody) )
                .build();
        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString() );

        assertEquals(201, response.statusCode());

        Review reviewResponse = mapper.readValue(response.body(), Review.class);
        assertNotEquals("aaaa", reviewResponse.getId());

        // Get the review from the database
        Review reviewFromDb = repo.findById(reviewResponse.getId()).get();

        // Validate that the DB object is the same as the original Review
        assertEquals(review.getStars(), reviewFromDb.getStars());
        assertEquals(review.getReview(), reviewFromDb.getReview());
        assertEquals(review.getPoiId(), reviewFromDb.getPoiId());
    }

    @Test
    public void createReviewInvalidPoiId() throws Exception {
        Review review = new Review(3, "A", poi1.getId());
        ObjectMapper mapper = new ObjectMapper();
        String jsonBody = mapper.writeValueAsString(review);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/poi/abcd/reviews"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody) )
                .build();
        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString() );

        assertEquals(404, response.statusCode());
    }

    @Test
    public void listAllReviews() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/poi/" + poi1.getId() + "/reviews"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString() );

        assertEquals(200, response.statusCode());
        Review[] responseReviews = mapper.readValue(response.body(), Review[].class);

        assertArrayEquals(reviews.toArray(), responseReviews);
    }

    @Test
    public void listAllReviewsEmpty() throws Exception {
        Poi newPoi = new Poi("A", "B", "C", 1, 1);
        newPoi = poiRepo.save(newPoi);
        ObjectMapper mapper = new ObjectMapper();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/poi/" + newPoi.getId() + "/reviews"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString() );

        assertEquals(200, response.statusCode());
        Review[] reviews = mapper.readValue(response.body(), Review[].class);

        assertEquals(0, reviews.length);

        poiRepo.deleteById(poi1.getId());
    }

    @Test
    public void deleteReview() throws Exception {
        Optional<Review> reviewOpt = repo.findById(reviews.get(0).getId());
        assertTrue(reviewOpt.isPresent());

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/reviews/" + reviews.get(0).getId()))
                .DELETE()
                .build();
        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString() );
        assertEquals(204, response.statusCode());

        reviewOpt = repo.findById(reviews.get(0).getId());
        assertTrue(reviewOpt.isEmpty());
    }

    @Test
    public void changeReview() throws Exception {
        Review reviewToChange = reviews.get(0);
        reviewToChange.setStars(2);
        reviewToChange.setReview("QQQQ");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(reviewToChange);

        HttpRequest req = HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .uri(URI.create("http://localhost:" + port + "/reviews/" + reviewToChange.getId()))
                .PUT(HttpRequest.BodyPublishers.ofString(json) )
                .build();

        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString() );
        assertEquals(200, response.statusCode());

        Review reviewFromDb = repo.findById(reviewToChange.getId()).get();
        assertEquals(reviewToChange, reviewFromDb);
    }

    @Test
    public void changeReviewNotExist() throws Exception {
        Review reviewToChange = reviews.get(0);
        reviewToChange.setStars(2);
        reviewToChange.setReview("QQQQ");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(reviewToChange);

        HttpRequest req = HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .uri(URI.create("http://localhost:" + port + "/reviews/abcd"))
                .PUT(HttpRequest.BodyPublishers.ofString(json) )
                .build();

        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString() );
        assertEquals(404, response.statusCode());
    }

    // This endpoint should ignore the ID that is provided in the body.  If the ID in the body
    // differs from the ID in the path, it should always use the path's ID.
    @Test
    public void changeReviewShouldIgnoreId() throws Exception {
        Review reviewToChange = reviews.get(0);
        String originalId = reviewToChange.getId();
        reviewToChange.setStars(2);
        reviewToChange.setReview("QQQQ");
        reviewToChange.setId("aaaa");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(reviewToChange);

        HttpRequest req = HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .uri(URI.create("http://localhost:" + port + "/reviews/" + originalId))
                .PUT(HttpRequest.BodyPublishers.ofString(json) )
                .build();

        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString() );
        assertEquals(200, response.statusCode());

        Review reviewFromDb = repo.findById(originalId).get();
        assertEquals(reviewToChange, reviewFromDb);
    }

    // Deleting a Poi should also delete the reviews for that Poi
    @Test
    public void deletePoiShouldAlsoDeleteReviews() throws Exception {
        List<Review> reviewsBefore = repo.findAllByPoiId(poi1.getId());
        assertEquals(2, reviewsBefore.size());

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/poi/" + poi1.getId()))
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString() );
        assertEquals(204, response.statusCode());

        List<Review> reviewsFromDb = repo.findAllByPoiId(poi1.getId());
        assertEquals(0, reviewsFromDb.size());
    }
}
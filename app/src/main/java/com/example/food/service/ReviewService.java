package com.example.food.service;

import android.util.Log;

import com.example.food.data.Review;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for loading review data from Firebase Firestore
 * This class handles data retrieval for reviews
 */
public class ReviewService {
    private static final String TAG = "ReviewService";
    private static final String COLLECTION_REVIEWS = "reviews";
    
    private FirebaseFirestore db;
    private CollectionReference reviewsRef;

    public ReviewService() {
        db = FirebaseFirestore.getInstance();
        reviewsRef = db.collection(COLLECTION_REVIEWS);
    }

    public interface ReviewsLoadCallback {
        void onSuccess(List<Review> reviews);
        void onError(Exception e);
    }
    
    public interface ReviewSaveCallback {
        void onSuccess();
        void onError(Exception e);
    }
    
    public interface OnReviewsLoadedListener {
        void onReviewsLoaded(List<Review> reviews);
        void onError(String error);
    }

    /**
     * Load all reviews ordered by createdAt (newest first)
     */
    public void loadReviews(ReviewsLoadCallback callback) {
        reviewsRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Review> reviews = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                Review review = document.toObject(Review.class);
                                review.setId(document.getId());
                                review.refreshAccuracyFromVotes(); // Calculate accuracy from votes
                                reviews.add(review);
                            } catch (Exception e) {
                                Log.w(TAG, "Error parsing review: " + document.getId(), e);
                            }
                        }
                        callback.onSuccess(reviews);
                        Log.d(TAG, "Loaded " + reviews.size() + " reviews from Firebase");
                    } else {
                        Log.w(TAG, "Error getting reviews from Firebase", task.getException());
                        callback.onError(task.getException());
                    }
                });
    }

    /**
     * Load reviews with pagination limit
     */
    public void loadReviewsWithLimit(int limit, ReviewsLoadCallback callback) {
        reviewsRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Review> reviews = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                Review review = document.toObject(Review.class);
                                review.setId(document.getId());
                                review.refreshAccuracyFromVotes(); // Calculate accuracy from votes
                                reviews.add(review);
                            } catch (Exception e) {
                                Log.w(TAG, "Error parsing review: " + document.getId(), e);
                            }
                        }
                        callback.onSuccess(reviews);
                        Log.d(TAG, "Loaded " + reviews.size() + " reviews with limit " + limit);
                    } else {
                        Log.w(TAG, "Error getting reviews with limit", task.getException());
                        callback.onError(task.getException());
                    }
                });
    }

    /**
     * Search reviews by description, caption, or restaurant name (client-side filtering)
     */
    public void searchReviews(String query, ReviewsLoadCallback callback) {
        // Load all reviews first, then filter on client side
        loadReviews(new ReviewsLoadCallback() {
            @Override
            public void onSuccess(List<Review> reviews) {
                List<Review> filteredReviews = new ArrayList<>();
                String lowerQuery = query.toLowerCase().trim();
                
                for (Review review : reviews) {
                    if ((review.getDescription() != null && review.getDescription().toLowerCase().contains(lowerQuery)) ||
                        (review.getCaption() != null && review.getCaption().toLowerCase().contains(lowerQuery)) ||
                        (review.getRestaurantName() != null && review.getRestaurantName().toLowerCase().contains(lowerQuery))) {
                        filteredReviews.add(review);
                    }
                }
                
                callback.onSuccess(filteredReviews);
                Log.d(TAG, "Search returned " + filteredReviews.size() + " reviews for query: " + query);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * Load reviews by specific user ID
     */
    public void getReviewsByUser(String userId, OnReviewsLoadedListener listener) {
        reviewsRef.whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Review> reviews = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                Review review = document.toObject(Review.class);
                                review.setId(document.getId());
                                review.refreshAccuracyFromVotes(); // Calculate accuracy from votes
                                reviews.add(review);
                            } catch (Exception e) {
                                Log.w(TAG, "Error parsing review: " + document.getId(), e);
                            }
                        }
                        listener.onReviewsLoaded(reviews);
                        Log.d(TAG, "Loaded " + reviews.size() + " reviews for user: " + userId);
                    } else {
                        Log.w(TAG, "Error getting reviews for user: " + userId, task.getException());
                        listener.onError(task.getException() != null ? task.getException().getMessage() : "Unknown error");
                    }
                });
    }
    
    /**
     * Save a new review to Firebase Firestore
     */
    public void saveReview(Review review, ReviewSaveCallback callback) {
        // Manually create a map to ensure userName and restaurantName are not stored
        Map<String, Object> reviewData = new HashMap<>();
        reviewData.put("userId", review.getUserId());
        reviewData.put("restaurantId", review.getRestaurantId());
        reviewData.put("caption", review.getCaption());
        reviewData.put("description", review.getDescription());
        reviewData.put("rating", review.getRating());
        reviewData.put("accuracyPercent", review.getAccuracyPercent());
        reviewData.put("imageUrls", review.getImageUrls());
        reviewData.put("firstImageType", review.getFirstImageType());
        reviewData.put("createdAt", review.getCreatedAt());
        reviewData.put("votes", review.getVotes());
        reviewData.put("comments", review.getComments());
        
        // Use auto-generated document ID. Do not store an explicit id/helpfulCount field
        reviewsRef
                .add(reviewData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Review saved successfully");
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving review", e);
                    callback.onError(e);
                });
    }
}

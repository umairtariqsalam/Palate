package com.example.food.utils;

import android.util.Log;

import com.example.food.data.Review;
import com.example.food.model.Restaurant;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScoreCalculator {
    private static final String TAG = "ScoreCalculator";

    public interface OnStatsCalculatedListener {
        void onStatsCalculated(Map<String, Object> stats, double credibilityScore, double experienceScore);
        void onError(String error);
    }

    public static double calculateCredibilityScore(Map<String, Object> stats) {
        if (stats == null) return 0.0;

        int accurateVotes = getIntValue(stats, "accurateVotes", 0);
        int inaccurateVotes = getIntValue(stats, "inaccurateVotes", 0);
        int totalVotes = getIntValue(stats, "totalVotes", 0);
        double avgAccuracyPercent = getDoubleValue(stats, "avgAccuracyPercent", 0.0);
        int totalCommentLikesReceived = getIntValue(stats, "totalCommentLikesReceived", 0);

        // base points for having reviews
        double baseScore = 0;
        int totalReviews = getIntValue(stats, "totalReviews", 0);
        if (totalReviews > 0) {
            baseScore = Math.min(10, totalReviews * 2);
        }

        // vote volume matters most
        double volumeScore = 0;
        if (totalVotes >= 10) {
            volumeScore = Math.log(totalVotes) * 15;
        } else if (totalVotes >= 5) {
            volumeScore = totalVotes * 2;
        } else if (totalVotes > 0) {
            volumeScore = totalVotes * 1;
        }

        // accuracy bonus
        double accuracyScore = 0;
        if (totalVotes >= 5) {
            if (avgAccuracyPercent >= 80) {
                accuracyScore = 20;
            } else if (avgAccuracyPercent >= 60) {
                accuracyScore = 10;
            } else if (avgAccuracyPercent < 40) {
                accuracyScore = -10;
            }
        } else if (totalVotes > 0) {
            if (avgAccuracyPercent >= 70) {
                accuracyScore = 5;
            }
        }

        // consistency bonus
        double consistencyBonus = 0;
        if (totalVotes >= 20 && avgAccuracyPercent >= 70) {
            consistencyBonus = Math.min(15, totalVotes / 10);
        }

        double commentBonus = totalCommentLikesReceived * 1;

        double credibilityScore = baseScore + volumeScore + accuracyScore + consistencyBonus + commentBonus;
        
        Log.d(TAG, String.format("Credibility: base=%.1f, volume=%.1f, accuracy=%.1f, consistency=%.1f, comment=%.1f, total=%.1f", 
                baseScore, volumeScore, accuracyScore, consistencyBonus, commentBonus, credibilityScore));
        
        return Math.max(0, Math.round(credibilityScore));
    }

    public static double calculateExperienceScore(Map<String, Object> stats) {
        if (stats == null) return 0.0;

        int totalReviews = getIntValue(stats, "totalReviews", 0);
        int uniqueRestaurants = getIntValue(stats, "uniqueRestaurants", 0);
        int uniqueCategories = getIntValue(stats, "uniqueCategories", 0);
        int uniqueRegions = getIntValue(stats, "uniqueRegions", 0);
        int repeatedRestaurants = getIntValue(stats, "repeatedRestaurants", 0);
        int totalCommentsMade = getIntValue(stats, "totalCommentsMade", 0);
        long daysActive = getLongValue(stats, "daysActive", 0);

        // base points for reviews
        double baseScore = totalReviews * 1;

        // activity over time
        double activityRate = 0;
        if (daysActive > 0 && totalReviews > 0) {
            double reviewsPerDay = (double) totalReviews / daysActive;
            if (reviewsPerDay >= 0.2) {
                activityRate = Math.min(20, reviewsPerDay * 50);
            }
        }

        // variety bonus
        double varietyMultiplier = 1.0;
        if (uniqueRestaurants >= 8) {
            varietyMultiplier = 1 + (uniqueRestaurants / 50.0);
        }
        baseScore *= varietyMultiplier;

        // different cuisines
        double categoryBonus = 0;
        if (uniqueCategories >= 6) {
            categoryBonus = uniqueCategories * 3;
        }

        // different areas
        double regionBonus = 0;
        if (uniqueRegions >= 4) {
            regionBonus = uniqueRegions * 5;
        }

        // revisiting places
        double deepDiveBonus = repeatedRestaurants * 3;

        // community stuff
        double participationBonus = 0;
        if (totalCommentsMade >= 20) {
            participationBonus = totalCommentsMade * 0.5;
        }

        // long term engagement
        double consistencyBonus = 0;
        if (daysActive >= 60 && totalReviews >= 20) {
            consistencyBonus = Math.min(15, daysActive / 20.0);
        }

        double experienceScore = baseScore + activityRate + categoryBonus + regionBonus + deepDiveBonus + participationBonus + consistencyBonus;
        
        Log.d(TAG, String.format("Experience: base=%.1f, activity=%.1f, variety=%.2f, category=%.1f, region=%.1f, deep=%.1f, participation=%.1f, consistency=%.1f, total=%.1f", 
                baseScore, activityRate, varietyMultiplier, categoryBonus, regionBonus, deepDiveBonus, participationBonus, consistencyBonus, experienceScore));
        
        return Math.max(0, Math.round(experienceScore));
    }

    public static void calculateUserStats(String userId, FirebaseFirestore db, OnStatsCalculatedListener listener) {
        Log.d(TAG, "Calculating stats for user: " + userId);

        // get user reviews
        db.collection("reviews")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Review> reviews = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Review review = document.toObject(Review.class);
                            review.setId(document.getId());
                            review.refreshAccuracyFromVotes(); // Calculate accuracy from votes
                            reviews.add(review);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing review: " + document.getId(), e);
                        }
                    }

                    if (reviews.isEmpty()) {
                        Map<String, Object> emptyStats = createEmptyStats();
                        listener.onStatsCalculated(emptyStats, 0.0, 0.0);
                        return;
                    }

                    // get restaurant ids
                    Set<String> restaurantIds = new HashSet<>();
                    for (Review review : reviews) {
                        if (review.getRestaurantId() != null) {
                            restaurantIds.add(review.getRestaurantId());
                        }
                    }

                    if (restaurantIds.isEmpty()) {
                        Map<String, Object> emptyStats = createEmptyStats();
                        listener.onStatsCalculated(emptyStats, 0.0, 0.0);
                        return;
                    }

                    fetchRestaurantsAndCalculateStats(db, reviews, restaurantIds, listener);

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching reviews for user: " + userId, e);
                    listener.onError("Failed to fetch user reviews: " + e.getMessage());
                });
    }

    private static void fetchRestaurantsAndCalculateStats(FirebaseFirestore db, List<Review> reviews, 
                                                         Set<String> restaurantIds, OnStatsCalculatedListener listener) {
        Map<String, Restaurant> restaurantMap = new HashMap<>();
        int[] fetchCount = {0};
        int totalRestaurants = restaurantIds.size();

        if (totalRestaurants == 0) {
            Map<String, Object> stats = calculateStatsFromData(reviews, restaurantMap);
            double credibilityScore = calculateCredibilityScore(stats);
            double experienceScore = calculateExperienceScore(stats);
            listener.onStatsCalculated(stats, credibilityScore, experienceScore);
            return;
        }

        for (String restaurantId : restaurantIds) {
            db.collection("restaurants")
                    .document(restaurantId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            try {
                                Restaurant restaurant = documentSnapshot.toObject(Restaurant.class);
                                if (restaurant != null) {
                                    restaurant.setId(documentSnapshot.getId());
                                    restaurantMap.put(restaurantId, restaurant);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing restaurant: " + restaurantId, e);
                            }
                        }

                        fetchCount[0]++;
                        if (fetchCount[0] >= totalRestaurants) {
                            // All restaurants fetched, calculate stats
                            Map<String, Object> stats = calculateStatsFromData(reviews, restaurantMap);
                            double credibilityScore = calculateCredibilityScore(stats);
                            double experienceScore = calculateExperienceScore(stats);
                            listener.onStatsCalculated(stats, credibilityScore, experienceScore);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching restaurant: " + restaurantId, e);
                        fetchCount[0]++;
                        if (fetchCount[0] >= totalRestaurants) {
                            // Continue with partial data
                            Map<String, Object> stats = calculateStatsFromData(reviews, restaurantMap);
                            double credibilityScore = calculateCredibilityScore(stats);
                            double experienceScore = calculateExperienceScore(stats);
                            listener.onStatsCalculated(stats, credibilityScore, experienceScore);
                        }
                    });
        }
    }

    private static Map<String, Object> calculateStatsFromData(List<Review> reviews, Map<String, Restaurant> restaurantMap) {
        Map<String, Object> stats = new HashMap<>();

        int totalReviews = reviews.size();
        stats.put("totalReviews", totalReviews);

        // how long user been active
        long daysActive = 0;
        if (!reviews.isEmpty()) {
            long firstReviewTime = Long.MAX_VALUE;
            for (Review review : reviews) {
                if (review.getCreatedAt() != null) {
                    firstReviewTime = Math.min(firstReviewTime, review.getCreatedAt().getTime());
                }
            }
            if (firstReviewTime != Long.MAX_VALUE) {
                long currentTime = System.currentTimeMillis();
                daysActive = Math.max(1, (currentTime - firstReviewTime) / (1000 * 60 * 60 * 24));
            }
        }
        stats.put("daysActive", daysActive);

        // count votes
        int totalVotes = 0;
        int accurateVotes = 0;
        int inaccurateVotes = 0;
        double totalAccuracyPercent = 0.0;
        int reviewsWithVotes = 0;

        for (Review review : reviews) {
            if (review.getVotes() != null && !review.getVotes().isEmpty()) {
                int reviewVotes = review.getVotes().size();
                totalVotes += reviewVotes;

                int reviewAccurate = 0;
                for (Map<String, Object> voteData : review.getVotes().values()) {
                    Boolean vote = (Boolean) voteData.get("accurate");
                    if (vote != null && vote) {
                        reviewAccurate++;
                    }
                }
                int reviewInaccurate = reviewVotes - reviewAccurate;

                accurateVotes += reviewAccurate;
                inaccurateVotes += reviewInaccurate;

                if (reviewVotes > 0) {
                    double reviewAccuracy = (reviewAccurate * 100.0) / reviewVotes;
                    totalAccuracyPercent += reviewAccuracy;
                    reviewsWithVotes++;
                }
            }
        }

        stats.put("totalVotes", totalVotes);
        stats.put("accurateVotes", accurateVotes);
        stats.put("inaccurateVotes", inaccurateVotes);
        stats.put("avgAccuracyPercent", reviewsWithVotes > 0 ? totalAccuracyPercent / reviewsWithVotes : 0.0);

        // count variety
        Set<String> uniqueRestaurantIds = new HashSet<>();
        Set<String> uniqueCategories = new HashSet<>();
        Set<String> uniqueRegions = new HashSet<>();
        Map<String, Integer> restaurantReviewCount = new HashMap<>();

        for (Review review : reviews) {
            if (review.getRestaurantId() != null) {
                uniqueRestaurantIds.add(review.getRestaurantId());
                
                restaurantReviewCount.put(review.getRestaurantId(), 
                    restaurantReviewCount.getOrDefault(review.getRestaurantId(), 0) + 1);

                Restaurant restaurant = restaurantMap.get(review.getRestaurantId());
                if (restaurant != null) {
                    if (restaurant.getCategory() != null) {
                        uniqueCategories.add(restaurant.getCategory());
                    }
                    if (restaurant.getRegion() != null) {
                        uniqueRegions.add(restaurant.getRegion());
                    }
                }
            }
        }

        stats.put("uniqueRestaurants", uniqueRestaurantIds.size());
        stats.put("uniqueCategories", uniqueCategories.size());
        stats.put("uniqueRegions", uniqueRegions.size());

        // restaurants reviewed more than once
        int repeatedRestaurants = 0;
        for (int count : restaurantReviewCount.values()) {
            if (count > 1) {
                repeatedRestaurants++;
            }
        }
        stats.put("repeatedRestaurants", repeatedRestaurants);

        // comments default to 0
        stats.put("totalCommentsMade", 0);
        stats.put("totalCommentsReceived", 0);
        stats.put("totalCommentLikesReceived", 0);

        Log.d(TAG, "Calculated stats: " + stats);
        return stats;
    }

    private static Map<String, Object> createEmptyStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalReviews", 0);
        stats.put("daysActive", 0);
        stats.put("totalVotes", 0);
        stats.put("accurateVotes", 0);
        stats.put("inaccurateVotes", 0);
        stats.put("avgAccuracyPercent", 0.0);
        stats.put("uniqueRestaurants", 0);
        stats.put("uniqueCategories", 0);
        stats.put("uniqueRegions", 0);
        stats.put("repeatedRestaurants", 0);
        stats.put("totalCommentsMade", 0);
        stats.put("totalCommentsReceived", 0);
        stats.put("totalCommentLikesReceived", 0);
        return stats;
    }

    private static int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private static double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private static long getLongValue(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }
}

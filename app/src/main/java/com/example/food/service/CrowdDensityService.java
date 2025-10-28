package com.example.food.service;

import android.util.Log;

import com.example.food.data.CrowdFeedback;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrowdDensityService {
    private static final String TAG = "CrowdDensityService";
    private static final String COLLECTION_CROWD_FEEDBACK = "crowdFeedback";
    
    private FirebaseFirestore db;
    private CollectionReference crowdFeedbackRef;

    public CrowdDensityService() {
        db = FirebaseFirestore.getInstance();
        crowdFeedbackRef = db.collection(COLLECTION_CROWD_FEEDBACK);
    }

    public interface CrowdDensityCallback {
        void onSuccess(CrowdDensityResult result);
        void onError(Exception e);
    }

    public interface FeedbackSubmitCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public static class CrowdDensityResult {
        private int crowdingLevel; // 1, 2, or 3
        private String statusText;
        private String description;
        private int colorResId;
        private int feedbackCount;
        private boolean hasRecentData;

        public CrowdDensityResult(int crowdingLevel, String statusText, String description, 
                                 int colorResId, int feedbackCount, boolean hasRecentData) {
            this.crowdingLevel = crowdingLevel;
            this.statusText = statusText;
            this.description = description;
            this.colorResId = colorResId;
            this.feedbackCount = feedbackCount;
            this.hasRecentData = hasRecentData;
        }

        // Getters
        public int getCrowdingLevel() { return crowdingLevel; }
        public String getStatusText() { return statusText; }
        public String getDescription() { return description; }
        public int getColorResId() { return colorResId; }
        public int getFeedbackCount() { return feedbackCount; }
        public boolean hasRecentData() { return hasRecentData; }
    }

    /**
     * Calculate crowd density for a restaurant based on recent feedback
     */
    public void calculateCrowdDensity(String restaurantId, CrowdDensityCallback callback) {
        // Get feedback from the last 60 minutes
        Timestamp oneHourAgo = new Timestamp(new java.util.Date(System.currentTimeMillis() - 60 * 60 * 1000));
        
        // First try a simple query without orderBy to avoid index issues
        crowdFeedbackRef
            .whereEqualTo("restaurantId", restaurantId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<CrowdFeedback> recentFeedbacks = new ArrayList<>();
                Map<String, CrowdFeedback> latestUserFeedback = new HashMap<>();
                
                // Process feedback and filter by time, keep only the latest from each user
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    try {
                        CrowdFeedback feedback = document.toObject(CrowdFeedback.class);
                        feedback.setId(document.getId());
                        
                        // Filter by time (last 60 minutes)
                        if (feedback.getTimestamp() != null && 
                            feedback.getTimestamp().compareTo(oneHourAgo) > 0) {
                            
                            // Keep only the latest feedback from each user
                            String userId = feedback.getUserId();
                            if (!latestUserFeedback.containsKey(userId) || 
                                feedback.getTimestamp().compareTo(latestUserFeedback.get(userId).getTimestamp()) > 0) {
                                latestUserFeedback.put(userId, feedback);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error parsing crowd feedback: " + document.getId(), e);
                    }
                }
                
                recentFeedbacks.addAll(latestUserFeedback.values());
                
                if (recentFeedbacks.isEmpty()) {
                    // No recent data - show default state instead of error
                    Log.d(TAG, "No recent feedback data for restaurant: " + restaurantId);
                    CrowdDensityResult result = new CrowdDensityResult(
                        0, "No Recent Data", "Be the first to share crowd status!", 
                        android.R.color.darker_gray, 0, false
                    );
                    callback.onSuccess(result);
                    return;
                }
                
                // Calculate weighted average
                double weightedSum = 0;
                double totalWeight = 0;
                
                for (CrowdFeedback feedback : recentFeedbacks) {
                    double weight = calculateTimeWeight(feedback.getTimestamp());
                    weightedSum += feedback.getCrowdingLevel() * weight;
                    totalWeight += weight;
                }
                
                double averageScore = totalWeight > 0 ? weightedSum / totalWeight : 0;
                int crowdingLevel = mapScoreToLevel(averageScore);
                
                CrowdDensityResult result = createCrowdDensityResult(crowdingLevel, recentFeedbacks.size());
                callback.onSuccess(result);
                
                Log.d(TAG, "Calculated crowd density for restaurant " + restaurantId + 
                      ": level=" + crowdingLevel + ", feedbacks=" + recentFeedbacks.size());
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error calculating crowd density for restaurant: " + restaurantId, e);
                Log.e(TAG, "Error details: " + e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("index")) {
                    Log.e(TAG, "This might be a Firestore index issue. Please create a composite index for crowdFeedback collection.");
                }
                callback.onError(e);
            });
    }

    /**
     * Submit crowd feedback
     */
    public void submitFeedback(String restaurantId, String userId, int crowdingLevel, 
                              FeedbackSubmitCallback callback) {
        // Check if user has already submitted feedback recently (within 15 minutes)
        Timestamp fifteenMinutesAgo = new Timestamp(new java.util.Date(System.currentTimeMillis() - 15 * 60 * 1000));
        
        crowdFeedbackRef
            .whereEqualTo("restaurantId", restaurantId)
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                // Check if user has submitted feedback within the last 15 minutes
                boolean hasRecentFeedback = false;
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    try {
                        CrowdFeedback feedback = document.toObject(CrowdFeedback.class);
                        if (feedback.getTimestamp() != null && 
                            feedback.getTimestamp().compareTo(fifteenMinutesAgo) > 0) {
                            hasRecentFeedback = true;
                            break;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error parsing feedback for duplicate check: " + document.getId(), e);
                    }
                }
                
                if (hasRecentFeedback) {
                    // User has already submitted feedback recently
                    Log.w(TAG, "User " + userId + " already submitted feedback recently");
                    callback.onError(new Exception("You cannot resubmit within 15 minutes!"));
                    return;
                }
                
                // Submit new feedback
                CrowdFeedback feedback = new CrowdFeedback(restaurantId, userId, crowdingLevel);
                crowdFeedbackRef.add(feedback)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "Feedback submitted successfully: " + documentReference.getId());
                        callback.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error submitting feedback", e);
                        callback.onError(e);
                    });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking recent feedback", e);
                callback.onError(e);
            });
    }

    /**
     * Calculate time weight based on how recent the feedback is
     */
    private double calculateTimeWeight(Timestamp feedbackTime) {
        long currentTime = System.currentTimeMillis();
        long feedbackTimeMillis = feedbackTime.toDate().getTime();
        long timeDiffMinutes = (currentTime - feedbackTimeMillis) / (60 * 1000);
        
        if (timeDiffMinutes <= 15) {
            return 4.0; // Most recent
        } else if (timeDiffMinutes <= 30) {
            return 3.0;
        } else if (timeDiffMinutes <= 45) {
            return 2.0;
        } else if (timeDiffMinutes <= 60) {
            return 1.0;
        } else {
            return 0.0; // Too old
        }
    }

    /**
     * Map calculated score to crowding level
     */
    private int mapScoreToLevel(double score) {
        if (score <= 1.5) {
            return 1; // Not Crowded
        } else if (score <= 2.5) {
            return 2; // Moderately Crowded
        } else {
            return 3; // Very Crowded
        }
    }

    /**
     * Create CrowdDensityResult based on crowding level
     */
    private CrowdDensityResult createCrowdDensityResult(int crowdingLevel, int feedbackCount) {
        switch (crowdingLevel) {
            case 1:
                return new CrowdDensityResult(
                    1, "Not Crowded", "Comfortable dining experience",
                    android.R.color.holo_green_light, feedbackCount, true
                );
            case 2:
                return new CrowdDensityResult(
                    2, "Moderately Crowded", "Some waiting time expected",
                    android.R.color.holo_orange_light, feedbackCount, true
                );
            case 3:
                return new CrowdDensityResult(
                    3, "Very Crowded", "Long waiting time, consider alternatives",
                    android.R.color.holo_red_light, feedbackCount, true
                );
            default:
                return new CrowdDensityResult(
                    0, "Unknown", "Unable to determine crowd level",
                    android.R.color.darker_gray, feedbackCount, false
                );
        }
    }
}

package com.example.food.data;

import com.google.firebase.firestore.Exclude;
import java.util.Date;
import java.util.List;

public class Review {
    private String id;
    private String userId;
    @Exclude
    private String userName; // Not stored in database. dynamically fetched
    @Exclude
    private String userAvatarUrl; // Not stored in database. dynamically fetched
    private String restaurantId;
    @Exclude
    private String restaurantName; // Not stored in database. dynamically fetched
    private String description;
    private String caption;
    private float rating;
    private int accuracy;
    private double accuracyPercent;
    private List<String> imageUrls;
    private String firstImageType;
    private Date createdAt;
    private Date updatedAt;
    private int helpfulCount;
    private java.util.Map<String, java.util.Map<String, Object>> votes;
    private List<Comment> comments;

    public Review() {
        // Default constructor required for Firestore
        this.votes = new java.util.HashMap<>();
        this.comments = new java.util.ArrayList<>();
        this.accuracyPercent = 0.0; // Default to 0% when no votes
    }

    public Review(String id, String userId, String userName, String restaurantId,
                 String restaurantName, String caption, String description, float rating,
                 int accuracy, List<String> imageUrls, Date createdAt) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
        this.caption = caption;
        this.description = description;
        this.rating = rating;
        this.accuracy = accuracy;
        this.imageUrls = imageUrls;
        this.createdAt = createdAt;
        this.helpfulCount = 0;
        this.accuracyPercent = 100.0;
        this.firstImageType = "SQUARE";
        this.votes = new java.util.HashMap<>();
        this.comments = new java.util.ArrayList<>();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserAvatarUrl() { return userAvatarUrl; }
    public void setUserAvatarUrl(String userAvatarUrl) { this.userAvatarUrl = userAvatarUrl; }

    public String getRestaurantId() { return restaurantId; }
    public void setRestaurantId(String restaurantId) { this.restaurantId = restaurantId; }

    public String getRestaurantName() { return restaurantName; }
    public void setRestaurantName(String restaurantName) { this.restaurantName = restaurantName; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }

    public int getAccuracy() { return accuracy; }
    public void setAccuracy(int accuracy) { this.accuracy = accuracy; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public String getFirstImageType() { return firstImageType; }
    public void setFirstImageType(String firstImageType) { this.firstImageType = firstImageType; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public int getHelpfulCount() { return helpfulCount; }
    public void setHelpfulCount(int helpfulCount) { this.helpfulCount = helpfulCount; }

    public double getAccuracyPercent() { return accuracyPercent; }
    public void setAccuracyPercent(double accuracyPercent) { this.accuracyPercent = accuracyPercent; }

    public java.util.Map<String, java.util.Map<String, Object>> getVotes() { return votes; }
    public void setVotes(java.util.Map<String, java.util.Map<String, Object>> votes) { this.votes = votes; }

    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }

    //if the first image is portrait based on stored image type
    //return true if the first image is portrait, false otherwise
    public boolean isFirstImagePortrait() {
        return "PORTRAIT".equals(firstImageType);
    }

    //if the first image is square based on stored image type
    //return true if the first image is square, false otherwise
    public boolean isFirstImageSquare() {
        return "SQUARE".equals(firstImageType);
    }

    //if the first image is horizontal based on stored image type
    //return true if the first image is horizontal, false otherwise
    public boolean isFirstImageHorizontal() {
        return "HORIZONTAL".equals(firstImageType);
    }

    /**
     * Calculate and update accuracy percentage from votes map
     * Call this method whenever votes change to keep accuracyPercent in sync
     */
    public void refreshAccuracyFromVotes() {
        this.accuracyPercent = calculateAccuracyFromVotes(this.votes);
    }

    /**
     * Static utility to calculate accuracy percentage from votes
     * @param votes the votes map
     * @return accuracy percentage (0-100)
     */
    public static double calculateAccuracyFromVotes(java.util.Map<String, java.util.Map<String, Object>> votes) {
        if (votes == null || votes.isEmpty()) {
            return 0.0;
        }

        int accurateVotes = 0;
        int totalVotes = votes.size();

        for (java.util.Map<String, Object> voteData : votes.values()) {
            Boolean vote = (Boolean) voteData.get("accurate");
            if (vote != null && vote) {
                accurateVotes++;
            }
        }

        return totalVotes > 0 ? (accurateVotes * 100.0) / totalVotes : 0.0;
    }
}
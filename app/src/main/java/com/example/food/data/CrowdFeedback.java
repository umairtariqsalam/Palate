package com.example.food.data;

import com.google.firebase.Timestamp;

public class CrowdFeedback {
    private String id;
    private String restaurantId;
    private String userId;
    private int crowdingLevel; // 1=Not Crowded, 2=Moderately Crowded, 3=Very Crowded
    private Timestamp timestamp;

    // Default constructor required for Firebase
    public CrowdFeedback() {
    }

    public CrowdFeedback(String restaurantId, String userId, int crowdingLevel) {
        this.restaurantId = restaurantId;
        this.userId = userId;
        this.crowdingLevel = crowdingLevel;
        this.timestamp = Timestamp.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(String restaurantId) {
        this.restaurantId = restaurantId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getCrowdingLevel() {
        return crowdingLevel;
    }

    public void setCrowdingLevel(int crowdingLevel) {
        this.crowdingLevel = crowdingLevel;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    // Helper methods
    public String getCrowdingLevelText() {
        switch (crowdingLevel) {
            case 1:
                return "Not Crowded";
            case 2:
                return "Moderately Crowded";
            case 3:
                return "Very Crowded";
            default:
                return "Unknown";
        }
    }

    public String getCrowdingLevelDescription() {
        switch (crowdingLevel) {
            case 1:
                return "Comfortable dining experience";
            case 2:
                return "Some waiting time expected";
            case 3:
                return "Long waiting time, consider alternatives";
            default:
                return "Status unknown";
        }
    }

    @Override
    public String toString() {
        return "CrowdFeedback{" +
                "id='" + id + '\'' +
                ", restaurantId='" + restaurantId + '\'' +
                ", userId='" + userId + '\'' +
                ", crowdingLevel=" + crowdingLevel +
                ", timestamp=" + timestamp +
                '}';
    }
}

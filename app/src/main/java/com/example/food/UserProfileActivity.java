package com.example.food;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.food.adapters.ReviewWidgetAdapter;
import com.example.food.data.Review;
import com.example.food.data.UserProfile;
import com.example.food.dialogs.ReviewDetailsDialog;
import com.example.food.model.Restaurant;
import com.example.food.service.ReviewService;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserProfileActivity extends AppCompatActivity {
    private static final String TAG = "UserProfileActivity";
    
    private String userId;
    private UserProfile userProfile;
    private FirebaseFirestore db;
    
    // Views
    private ImageView ivProfilePicture;
    private TextView tvUserName;
    private TextView tvUserBio;
    private TextView tvCredibilityScore;
    private TextView tvExperienceScore;
    private TextView tvReviewsCount;
    private RecyclerView rvUserReviews;
    
    private ReviewWidgetAdapter reviewAdapter;
    private List<Review> userReviews;
    private Map<String, Restaurant> restaurantMap;
    private ReviewService reviewService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);
        
        // Get userId from intent
        userId = getIntent().getStringExtra("userId");
        if (userId == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        initViews();
        initData();
        loadUserProfile();
        loadUserReviews();
    }
    
    private void initViews() {
        ivProfilePicture = findViewById(R.id.ivProfilePicture);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserBio = findViewById(R.id.tvUserBio);
        tvCredibilityScore = findViewById(R.id.tvCredibilityScore);
        tvExperienceScore = findViewById(R.id.tvExperienceScore);
        tvReviewsCount = findViewById(R.id.tvReviewsCount);
        rvUserReviews = findViewById(R.id.rvUserReviews);
        
        // Setup back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        // Setup reviews RecyclerView
        rvUserReviews.setLayoutManager(new LinearLayoutManager(this));
        userReviews = new ArrayList<>();
        restaurantMap = new HashMap<>();
        reviewAdapter = new ReviewWidgetAdapter(userReviews, new ReviewWidgetAdapter.OnReviewClickListener() {
            @Override
            public void onReviewClick(Review review, Restaurant restaurant) {
                // Open review details dialog
                ReviewDetailsDialog dialog = new ReviewDetailsDialog(UserProfileActivity.this, review, restaurant);
                dialog.show();
            }
            
            @Override
            public void onUserClick(String clickedUserId) {
                // Handle user click - navigate to their profile
                if (!clickedUserId.equals(userId)) {
                    Intent intent = new Intent(UserProfileActivity.this, UserProfileActivity.class);
                    intent.putExtra("userId", clickedUserId);
                    startActivity(intent);
                }
            }
        }, false);
        rvUserReviews.setAdapter(reviewAdapter);
    }
    
    private void initData() {
        db = FirebaseFirestore.getInstance();
        reviewService = new ReviewService();
    }
    
    private void loadUserProfile() {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    userProfile = documentSnapshot.toObject(UserProfile.class);
                    if (userProfile != null) {
                        updateUserUI();
                    }
                } else {
                    showUserNotFound();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading user profile", e);
                Toast.makeText(this, "Failed to load user profile", Toast.LENGTH_SHORT).show();
            });
    }
    
    private void updateUserUI() {
        if (userProfile == null) return;
        
        // Set user name
        String userName = userProfile.getName();
        if (userName != null && !userName.trim().isEmpty()) {
            tvUserName.setText(userName);
        } else {
            tvUserName.setText("Unknown User");
        }
        
        // Set user bio
        String bio = userProfile.getBio();
        if (bio != null && !bio.trim().isEmpty()) {
            tvUserBio.setText(bio);
            tvUserBio.setVisibility(View.VISIBLE);
        } else {
            tvUserBio.setVisibility(View.GONE);
        }
        
        // Set credibility score
        tvCredibilityScore.setText(String.format(Locale.getDefault(), "%.0f", userProfile.getCredibilityScore()));
        
        // Set experience score
        tvExperienceScore.setText(String.format(Locale.getDefault(), "%.0f", userProfile.getExperienceScore()));
        
        // Load profile picture
        String avatarUrl = userProfile.getAvatarUrl();
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(ivProfilePicture);
        } else {
            ivProfilePicture.setImageResource(R.drawable.ic_person);
        }
    }
    
    private void loadUserReviews() {
        reviewService.getReviewsByUser(userId, new ReviewService.OnReviewsLoadedListener() {
            @Override
            public void onReviewsLoaded(List<Review> reviews) {
                userReviews.clear();
                userReviews.addAll(reviews);
                
                // Update reviews count
                tvReviewsCount.setText(String.format(Locale.getDefault(), "%d reviews", reviews.size()));
                
                // Load restaurant information for reviews
                loadRestaurantInfoForReviews(reviews);
                
                reviewAdapter.notifyDataSetChanged();
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading user reviews: " + error);
                Toast.makeText(UserProfileActivity.this, "Failed to load reviews", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void loadRestaurantInfoForReviews(List<Review> reviews) {
        restaurantMap.clear();
        
        for (Review review : reviews) {
            String restaurantId = review.getRestaurantId();
            if (restaurantId != null && !restaurantMap.containsKey(restaurantId)) {
                // Load restaurant info from Firestore
                db.collection("restaurants").document(restaurantId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Restaurant restaurant = documentSnapshot.toObject(Restaurant.class);
                            if (restaurant != null) {
                                restaurantMap.put(restaurantId, restaurant);
                                reviewAdapter.setRestaurantMap(restaurantMap);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading restaurant: " + restaurantId, e);
                    });
            }
        }
    }
    
    private void showUserNotFound() {
        tvUserName.setText("User Not Found");
        tvUserBio.setVisibility(View.GONE);
        tvCredibilityScore.setText("0");
        tvExperienceScore.setText("0");
        tvReviewsCount.setText("0 reviews");
        ivProfilePicture.setImageResource(R.drawable.ic_person);
    }
}

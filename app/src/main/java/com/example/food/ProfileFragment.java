package com.example.food;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import android.view.View;


import com.bumptech.glide.Glide;
import com.example.food.adapters.ActivityAdapter;
import com.example.food.adapters.GroupedActivityAdapter;
import com.example.food.adapters.ReviewWidgetAdapter;
import com.example.food.cache.ProfileCacheManager;
import com.example.food.data.ActivityItem;
import com.example.food.data.Review;
import com.example.food.data.UserProfile;
import com.example.food.dialogs.ReviewDetailsDialog;
import com.example.food.model.Restaurant;
import com.example.food.services.UserStatsService;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    
    // Views
    private de.hdodenhof.circleimageview.CircleImageView ivProfilePicture;
    private TextView tvUsername, tvBio, tvCredibilityScore, tvExperienceScore, tvEngagementScore;
    private RecyclerView rvReviews;
    private LinearLayout emptyState;
    private LinearLayout credibilityCard, experienceCard, engagementCard;
    
    // Tab views
    private TabLayout tabLayout;
    private androidx.constraintlayout.widget.ConstraintLayout reviewsContainer;
    private androidx.constraintlayout.widget.ConstraintLayout activityContainer;
    private androidx.constraintlayout.widget.ConstraintLayout statsContentContainer;
    private RecyclerView rvActivity;
    private LinearLayout emptyActivityLayout;
    
    // Adapters and data
    private ReviewWidgetAdapter reviewAdapter;
    private GroupedActivityAdapter activityAdapter;
    private List<Review> reviews;
    private List<ActivityItem> activities;
    private Map<String, Restaurant> restaurantMap;
    private Map<String, Review> reviewMap;
    private UserProfile userProfile;
    
    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration profileListener;
    private ListenerRegistration activityListener;
    private ProfileCacheManager cacheManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        initFirebase();
        setupRecyclerView();
        setupActivityRecyclerView();
        setupTabLayout();
        setupCardClickListeners();
        setupAnalyticsClickListeners();
        loadUserData();
        setupReviews();
    }

    @Override
    public void onResume() {
        super.onResume();
        // refresh user data when returning to profile
        loadUserData();
    }

    private void initViews(View view) {
        ivProfilePicture = view.findViewById(R.id.ivAvatar);
        tvUsername = view.findViewById(R.id.tvDisplayName);
        tvBio = view.findViewById(R.id.tvBio);
        tvCredibilityScore = view.findViewById(R.id.tvCredibilityScore);
        tvExperienceScore = view.findViewById(R.id.tvExperienceScore);
        tvEngagementScore = view.findViewById(R.id.tvEngagementScore);
        rvReviews = view.findViewById(R.id.rvReviews);
        emptyState = view.findViewById(R.id.emptyReviewsLayout);
        
        // Tab views
        tabLayout = view.findViewById(R.id.tabLayout);
        reviewsContainer = view.findViewById(R.id.reviewsContainer);
        activityContainer = view.findViewById(R.id.activityContainer);
        statsContentContainer = view.findViewById(R.id.statsContentContainer);
        rvActivity = view.findViewById(R.id.rvActivity);
        emptyActivityLayout = view.findViewById(R.id.emptyActivityLayout);
        
        // Metric cards - engagement one removed for now in ui seems unnecessary
        credibilityCard = view.findViewById(R.id.credibilityCard);
        experienceCard = view.findViewById(R.id.experienceCard);
        engagementCard = view.findViewById(R.id.engagementCard);
    }

    private void initFirebase() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        cacheManager = ProfileCacheManager.getInstance(requireContext());
    }

    private void setupCardClickListeners() {
        if (credibilityCard != null) {
            credibilityCard.setOnClickListener(v -> showMetricInfoBottomSheet(
                getString(R.string.profile_metric_credibility_title),
                getString(R.string.profile_metric_credibility_body),
                getString(R.string.credibility_score_interpretation),
                "credibility"
            ));
        }

        if (experienceCard != null) {
            experienceCard.setOnClickListener(v -> showMetricInfoBottomSheet(
                getString(R.string.profile_metric_experience_title),
                getString(R.string.profile_metric_experience_body),
                getString(R.string.experience_score_interpretation),
                "experience"
            ));
        }

        if (engagementCard != null) {
            engagementCard.setOnClickListener(v -> showMetricInfoBottomSheet(
                getString(R.string.engagement_score_title),
                getString(R.string.engagement_score_body),
                getString(R.string.engagement_score_interpretation),
                "engagement"
            ));
        }
    }

    private void setupAnalyticsClickListeners() {
        View view = getView();
        if (view == null) return;

        // Total Reviews Card
        LinearLayout totalReviewsCard = view.findViewById(R.id.totalReviewsCard);
        if (totalReviewsCard != null) {
            totalReviewsCard.setOnClickListener(v -> showAnalyticsBottomSheet(
                "Total Reviews",
                "This shows the total number of reviews you've written. Keep writing reviews to help others discover great places to eat!",
                "reviews"
            ));
        }

        // Average Rating Card
        LinearLayout averageRatingCard = view.findViewById(R.id.averageRatingCard);
        if (averageRatingCard != null) {
            averageRatingCard.setOnClickListener(v -> showAnalyticsBottomSheet(
                "Average Accuracy",
                "This shows your average accuracy percentage based on community votes. Higher accuracy means more people found your reviews helpful and accurate.",
                "accuracy"
            ));
        }

        // Total Restaurants Card
        LinearLayout totalRestaurantsCard = view.findViewById(R.id.totalRestaurantsCard);
        if (totalRestaurantsCard != null) {
            totalRestaurantsCard.setOnClickListener(v -> showAnalyticsBottomSheet(
                "Unique Restaurants",
                "This shows the number of different restaurants you've reviewed. Explore more places to increase your restaurant diversity!",
                "restaurants"
            ));
        }

        // Total Votes Card
        LinearLayout totalVotesCard = view.findViewById(R.id.totalVotesCard);
        if (totalVotesCard != null) {
            totalVotesCard.setOnClickListener(v -> showAnalyticsBottomSheet(
                "Community Votes",
                "This shows the total number of votes (accurate/inaccurate) your reviews have received from the community.",
                "votes"
            ));
        }
    }

    private void showAnalyticsBottomSheet(String title, String message, String metricType) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View view = View.inflate(requireContext(), R.layout.bottom_sheet_analytics_detail, null);

        TextView tvTitle = view.findViewById(R.id.tvAnalyticsTitle);
        TextView tvMessage = view.findViewById(R.id.tvAnalyticsMessage);
        TextView tvValue = view.findViewById(R.id.tvAnalyticsValue);
        ImageView btnClose = view.findViewById(R.id.btnClose);

        tvTitle.setText(title);
        tvMessage.setText(message);

        // Set the current value based on metric type
        if (userProfile != null && userProfile.getStats() != null) {
            Map<String, Object> stats = userProfile.getStats();
            switch (metricType) {
                case "reviews":
                    Object totalReviews = stats.get("totalReviews");
                    tvValue.setText(totalReviews != null ? totalReviews.toString() : "0");
                    break;
                case "accuracy":
                    Object avgAccuracy = stats.get("avgAccuracyPercent");
                    if (avgAccuracy != null) {
                        tvValue.setText(String.format("%.0f%%", ((Number) avgAccuracy).doubleValue()));
                    } else {
                        tvValue.setText("0%");
                    }
                    break;
                case "restaurants":
                    Object uniqueRestaurants = stats.get("uniqueRestaurants");
                    tvValue.setText(uniqueRestaurants != null ? uniqueRestaurants.toString() : "0");
                    break;
                case "votes":
                    Object totalVotes = stats.get("totalVotes");
                    tvValue.setText(totalVotes != null ? totalVotes.toString() : "0");
                    break;
                default:
                    tvValue.setText("0");
                    break;
            }
        } else {
            tvValue.setText("0");
        }

        btnClose.setOnClickListener(v -> sheet.dismiss());

        sheet.setContentView(view);
        sheet.show();
    }

    private void showMetricInfoBottomSheet(String title, String message, String interpretation, String metricType) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View view = View.inflate(requireContext(), R.layout.bottom_sheet_score_detail, null);

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvMessage = view.findViewById(R.id.tvMessage);
        ImageView ivScoreIcon = view.findViewById(R.id.ivScoreIcon);
        TextView tvScoreLabel = view.findViewById(R.id.tvScoreLabel);
        TextView tvScoreValue = view.findViewById(R.id.tvScoreValue);
        TextView tvScoreBreakdown = view.findViewById(R.id.tvScoreBreakdown);
        ImageView btnClose = view.findViewById(R.id.btnClose);

        tvTitle.setText(title);
        tvMessage.setText(message);

        // Set icon, color and label based on metric types (currently only credibility and experience)
        int scoreColor = 0;
        switch (metricType) {
            case "credibility":
                ivScoreIcon.setImageResource(R.drawable.ic_verified_user);
                scoreColor = getResources().getColor(R.color.logo_accent);
                ivScoreIcon.setColorFilter(scoreColor);
                tvScoreLabel.setText(getString(R.string.credibility_label));
                tvScoreLabel.setTextColor(scoreColor);
                break;
            case "experience":
                ivScoreIcon.setImageResource(R.drawable.ic_trending_up);
                scoreColor = getResources().getColor(R.color.profile_warning);
                ivScoreIcon.setColorFilter(scoreColor);
                tvScoreLabel.setText(getString(R.string.experience_label));
                tvScoreLabel.setTextColor(scoreColor);
                break;
            case "engagement":
                ivScoreIcon.setImageResource(R.drawable.ic_comments);
                scoreColor = getResources().getColor(R.color.profile_success);
                ivScoreIcon.setColorFilter(scoreColor);
                tvScoreLabel.setText(getString(R.string.engagement_label));
                tvScoreLabel.setTextColor(scoreColor);
                break;
        }
        
        // Set the score value color to match the card - ui stuff
        tvScoreValue.setTextColor(scoreColor);

        loadMetricScoreWithBreakdown(metricType, tvScoreValue, tvScoreBreakdown, interpretation);

        btnClose.setOnClickListener(v -> sheet.dismiss());

        sheet.setContentView(view);
        sheet.show();
    }

    private void loadMetricScoreWithBreakdown(String metricType, TextView scoreValueView, TextView breakdownView, String baseInterpretation) {
        if (auth.getCurrentUser() == null) return;

        // fetch fresh scores from firestore to ensure consistency with cards
        UserStatsService.getUserScores(auth.getCurrentUser().getUid(), new UserStatsService.OnScoresRetrievedListener() {
            @Override
            public void onScoresRetrieved(double credibilityScore, double experienceScore) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        int score = 0;
                        String breakdown = "";
                        
                        switch (metricType) {
                            case "credibility":
                                score = (int) Math.round(credibilityScore);
                                breakdown = getCredibilityBreakdown();
                                break;
                            case "experience":
                                score = (int) Math.round(experienceScore);
                                breakdown = getExperienceBreakdown();
                                break;
                            case "engagement":
                                score = 0;
                                breakdown = "Engagement score coming soon!";
                                break;
                        }
                        
                        // Set the score value in the card UI
                        scoreValueView.setText(formatScore(score));
                        
                        // Set the breakdown text
                        breakdownView.setText(breakdown);
                    });
                }
            }
        });
    }

    private String getCredibilityBreakdown() {
        return getString(R.string.credibility_breakdown);
    }

    private String getExperienceBreakdown() {
        return getString(R.string.experience_breakdown);
    }

    private void loadMetricScore(String metricType, TextView scoreView, String baseInterpretation) {
        if (auth.getCurrentUser() == null) return;

        db.collection("users")
            .document(auth.getCurrentUser().getUid())
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    int score = 0;
                    switch (metricType) {
                        case "credibility":
                            score = documentSnapshot.getLong("credibilityScore") != null ? 
                                   documentSnapshot.getLong("credibilityScore").intValue() : 0;
                            break;
                        case "experience":
                            score = documentSnapshot.getLong("experienceScore") != null ? 
                                   documentSnapshot.getLong("experienceScore").intValue() : 0;
                            break;
                        case "engagement":
                            score = documentSnapshot.getLong("engagementScore") != null ? 
                                   documentSnapshot.getLong("engagementScore").intValue() : 0;
                            break;
                    }
                    
                    String dynamicInterpretation = baseInterpretation + "\n\nCurrent Score: " + score;
                    scoreView.setText(dynamicInterpretation);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading metric score for " + metricType, e);
            
            });
    }

    private void setupRecyclerView() {
        reviews = new ArrayList<>();
        restaurantMap = new HashMap<>();
        
        reviewAdapter = new ReviewWidgetAdapter(new ArrayList<>(), new ReviewWidgetAdapter.OnReviewClickListener() {
            @Override
            public void onReviewClick(Review review, Restaurant restaurant) {
                showReviewDetails(review, restaurant);
            }
            
            @Override
            public void onUserClick(String userId) {
                // Navigate to user profile
                Intent intent = new Intent(getActivity(), UserProfileActivity.class);
                intent.putExtra("userId", userId);
                startActivity(intent);
            }
        }, false);
        
        // Use StaggeredGridLayoutManager for diff image sizes
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        rvReviews.setLayoutManager(layoutManager);
        
        // Add spacing between items so it looks better
        int spacing = (int) (4 * getResources().getDisplayMetrics().density);
        rvReviews.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.left = spacing / 2;
                outRect.right = spacing / 2;
                outRect.top = spacing;
                outRect.bottom = spacing;
            }
        });
        rvReviews.setAdapter(reviewAdapter);
    }

    private void setupActivityRecyclerView() {
        activities = new ArrayList<>();
        reviewMap = new HashMap<>();
        
        activityAdapter = new GroupedActivityAdapter(new ArrayList<>(), new GroupedActivityAdapter.OnActivityClickListener() {
            @Override
            public void onActivityClick(ActivityItem activity, Review review, Restaurant restaurant) {
                if (review != null) {
                    if (activity.getType() == ActivityItem.ActivityType.COMMENT) {
                        // For comment activities show review details and open comments
                        showReviewDetailsWithComments(review, restaurant);
                    } else {
                        // for vote activities just show review details normally
                        showReviewDetails(review, restaurant);
                    }
                }
            }
        });
        
        rvActivity.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        rvActivity.setAdapter(activityAdapter);
    }

    private void setupTabLayout() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                switch (position) {
                    case 0: // Reviews
                        showReviewsTab();
                        break;
                    case 1: // Activity
                        showActivityTab();
                        break;
                    case 2: // Analytics
                        showAnalyticsTab();
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        
        // Show reviews tab by default
        showReviewsTab();
    }

    private void showReviewsTab() {
        reviewsContainer.setVisibility(View.VISIBLE);
        activityContainer.setVisibility(View.GONE);
        statsContentContainer.setVisibility(View.GONE);
    }

    private void showActivityTab() {
        reviewsContainer.setVisibility(View.GONE);
        activityContainer.setVisibility(View.VISIBLE);
        statsContentContainer.setVisibility(View.GONE);
        
        // Load real activity data from Firestore
        setupActivityFeed();
    }

    private void showAnalyticsTab() {
        reviewsContainer.setVisibility(View.GONE);
        activityContainer.setVisibility(View.GONE);
        statsContentContainer.setVisibility(View.VISIBLE);
        
        // Load analytics data when tab is shown
        loadFreshAnalyticsData();
    }

    private void loadUserData() {
        if (auth.getCurrentUser() == null) {
            Log.w(TAG, "User not authenticated");
            return;
        }

        loadCachedData();
        
        tvCredibilityScore.setText(getString(R.string.credibility_placeholder));
        tvExperienceScore.setText(getString(R.string.experience_placeholder));
        tvEngagementScore.setText(getString(R.string.engagement_placeholder));
        
        loadUserProfileOnce();
    }

    private void loadCachedData() {
        UserProfile cachedProfile = cacheManager.getCachedUserProfile();
        if (cachedProfile != null) {
            userProfile = cachedProfile;
            updateUserUI();
        }
    }

    private void loadUserProfileOnce() {
        if (auth.getCurrentUser() == null) return;

        String userId = auth.getCurrentUser().getUid();
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot != null && documentSnapshot.exists()) {
                    userProfile = documentSnapshot.toObject(UserProfile.class);
                    if (userProfile != null) {
                        cacheManager.cacheUserProfile(userProfile);
                        updateUserUI();
                        UserStatsService.updateUserScores(userId);
                    }
                } else {
                    // Create default profile if doesn't exist
                    createDefaultProfile();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading user profile", e);
            });
    }

    private void updateUserUI() {
        if (userProfile == null) return;

        String name = userProfile.getName();
        if (name == null || name.trim().isEmpty()) {
            name = getString(R.string.username_placeholder);
        }
        tvUsername.setText(name);

        String bio = userProfile.getBio();
        if (bio == null || bio.trim().isEmpty()) {
            bio = getString(R.string.bio_placeholder);
        }
        tvBio.setText(bio);

        loadProfilePicture();
        updateScoreDisplays();
    }

    private void updateScoreDisplays() {
        if (auth.getCurrentUser() == null) return;

        // always fetch fresh scores from firestore to ensure consistency
        UserStatsService.getUserScores(auth.getCurrentUser().getUid(), new UserStatsService.OnScoresRetrievedListener() {
            @Override
            public void onScoresRetrieved(double credibilityScore, double experienceScore) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tvCredibilityScore.setText(formatScore(credibilityScore));
                        tvExperienceScore.setText(formatScore(experienceScore));
                        tvEngagementScore.setText("0");
                    });
                }
            }
        });
    }

    private String formatScore(double score) {
        int roundedScore = (int) Math.round(score);
        
        if (roundedScore >= 1000) {
            return String.format("%.1fk", roundedScore / 1000.0);
        } else {
            return String.valueOf(roundedScore);
        }
    }

    private void createDefaultProfile() {
        if (auth.getCurrentUser() == null) return;

        String name = auth.getCurrentUser().getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            name = auth.getCurrentUser().getEmail().split("@")[0];
        }

        userProfile = new UserProfile(
            auth.getCurrentUser().getUid(),
            name,
            auth.getCurrentUser().getEmail(),
            getString(R.string.bio_placeholder)
        );

        updateUserUI();

        db.collection("users").document(auth.getCurrentUser().getUid())
            .set(userProfile)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "User profile created successfully"))
            .addOnFailureListener(e -> Log.e(TAG, "Error creating user profile", e));
    }

    private void loadProfilePicture() {
        if (userProfile == null) {
            ivProfilePicture.setBorderWidth(0);
            ivProfilePicture.setImageResource(R.drawable.ic_person);
            return;
        }

        String avatarUrl = userProfile.getAvatarUrl();
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            // show border when image exists
            ivProfilePicture.setBorderWidth(4);
            Glide.with(requireContext())
                    .load(avatarUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .centerCrop()
                    .override(140, 140)
                    .into(ivProfilePicture);
        } else {
            // no border for placeholder
            ivProfilePicture.setBorderWidth(0);
            ivProfilePicture.setImageResource(R.drawable.ic_person);
        }
    }

    private void setupReviews() {
        if (auth.getCurrentUser() == null) {
            showEmptyState();
            return;
        }

        String userId = auth.getCurrentUser().getUid();

        reviewAdapter.setLoading(true);

        if (profileListener != null) {
            profileListener.remove();
        }
        profileListener = db.collection("reviews")
            .whereEqualTo("userId", userId)
            .limit(200)
            .addSnapshotListener((queryDocumentSnapshots, e) -> {
                if (e != null) {
                    Log.e(TAG, "Error listening to reviews", e);
                    reviewAdapter.setLoading(false);
                    showEmptyState();
                    return;
                }

                if (queryDocumentSnapshots != null) {
                    reviews.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Review review = document.toObject(Review.class);
                            review.setId(document.getId());
                            reviews.add(review);
                        } catch (Exception ex) {
                            Log.e(TAG, "Error parsing review document", ex);
                        }
                    }

                    reviewAdapter.setLoading(false);

                    if (reviews.isEmpty()) {
                        showEmptyState();
                    } else {
                        hideEmptyState();
                        loadRestaurants();
                    }
                }
            });
    }

    private void loadRestaurants() {
        if (reviews.isEmpty()) return;

        List<String> restaurantIds = new ArrayList<>();
        for (Review review : reviews) {
            if (review.getRestaurantId() != null && !restaurantIds.contains(review.getRestaurantId())) {
                restaurantIds.add(review.getRestaurantId());
            }
        }

        if (restaurantIds.isEmpty()) {
            updateReviews();
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
                            Log.e(TAG, "Error parsing restaurant document", e);
                        }
                    }
                    updateReviews();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading restaurant: " + restaurantId, e);
                    updateReviews();
                });
        }
    }

    private void updateReviews() {
        reviewAdapter.setReviews(reviews);
        reviewAdapter.setRestaurantMap(restaurantMap);
    }

    private void showReviewDetails(Review review, Restaurant restaurant) {
        if (getContext() == null) return;
        
        ReviewDetailsDialog dialog = new ReviewDetailsDialog(getContext(), review, restaurant);
        dialog.show();
    }
    
    private void showReviewDetailsWithComments(Review review, Restaurant restaurant) {
        if (getContext() == null) return;
        
        ReviewDetailsDialog dialog = new ReviewDetailsDialog(getContext(), review, restaurant);
        dialog.show();
        
        // Open comments section after a short delay so that dialog is fully loaded
        dialog.getWindow().getDecorView().postDelayed(() -> {
            dialog.openCommentsSection();
        }, 300);
    }

    private void showEmptyState() {
        if (emptyState != null) {
            emptyState.setVisibility(View.VISIBLE);
        }
        if (rvReviews != null) {
            rvReviews.setVisibility(View.GONE);
        }
    }

    private void hideEmptyState() {
        if (emptyState != null) {
            emptyState.setVisibility(View.GONE);
        }
        if (rvReviews != null) {
            rvReviews.setVisibility(View.VISIBLE);
        }
    }

    private void setupActivityFeed() {
        if (auth.getCurrentUser() == null) {
            showEmptyActivityState();
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        activities.clear();

        // Load initial activities
        loadInitialActivities(userId);
        
        // Set up real-time listener for activities
        setupActivityListener(userId);
    }
    
    private void loadInitialActivities(String userId) {
        // Query all reviews by current user
        db.collection("reviews")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<ActivityItem> allActivities = new ArrayList<>();
                
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    try {
                        Review review = document.toObject(Review.class);
                        review.setId(document.getId());
                        reviewMap.put(review.getId(), review);
                        
                        // Process votes - only show accurate votes
                        if (review.getVotes() != null) {
                            for (Map.Entry<String, Map<String, Object>> voteEntry : review.getVotes().entrySet()) {
                                String voterId = voteEntry.getKey();
                                Map<String, Object> voteData = voteEntry.getValue();
                                Boolean voteType = (Boolean) voteData.get("accurate");
                                java.util.Date voteTimestamp = null;
                                Object timestampObj = voteData.get("timestamp");
                                if (timestampObj instanceof com.google.firebase.Timestamp) {
                                    voteTimestamp = ((com.google.firebase.Timestamp) timestampObj).toDate();
                                } else if (timestampObj instanceof java.util.Date) {
                                    voteTimestamp = (java.util.Date) timestampObj;
                                }
                                
                                // Skip if it's the review author voting on their own review
                                // Only show accurate votes
                                if (!voterId.equals(userId) && voteType != null && voteType) {
                                    ActivityItem activity = new ActivityItem(
                                        ActivityItem.ActivityType.VOTE,
                                        voterId,
                                        null, // Will be filled later
                                        null, // Will be filled later
                                        review.getId(),
                                        review.getCaption(),
                                        review.getRestaurantName(),
                                        voteTimestamp != null ? voteTimestamp : (review.getUpdatedAt() != null ? review.getUpdatedAt() : review.getCreatedAt())
                                    );
                                    activity.setVoteType(voteType);
                                    // Set the first image URL from the review
                                    if (review.getImageUrls() != null && !review.getImageUrls().isEmpty()) {
                                        activity.setReviewFirstImageUrl(review.getImageUrls().get(0));
                                    }
                                    allActivities.add(activity);
                                }
                            }
                        }
                        
                        // Process comments (if they exist)
                        if (review.getComments() != null) {
                            for (com.example.food.data.Comment comment : review.getComments()) {
                                // Skip if it's the review author commenting on their own review
                                if (!comment.getUserId().equals(userId)) {
                                    ActivityItem activity = new ActivityItem(
                                        ActivityItem.ActivityType.COMMENT,
                                        comment.getUserId(),
                                        null, // Will be filled later
                                        null, // Will be filled later
                                        review.getId(),
                                        review.getCaption(),
                                        review.getRestaurantName(),
                                        comment.getCreatedAt()
                                    );
                                    activity.setCommentText(comment.getText());
                                    // Set the first image URL from the review
                                    if (review.getImageUrls() != null && !review.getImageUrls().isEmpty()) {
                                        activity.setReviewFirstImageUrl(review.getImageUrls().get(0));
                                    }
                                    allActivities.add(activity);
                                }
                            }
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing review document", e);
                    }
                }
                
                // Filter to only show last 30 days no more than that
                long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
                List<ActivityItem> recentActivities = new ArrayList<>();
                for (ActivityItem activity : allActivities) {
                    if (activity.getTimestamp() != null && activity.getTimestamp().getTime() >= thirtyDaysAgo) {
                        recentActivities.add(activity);
                    }
                }

                // Sort by timestamp descending (most recent first)
                recentActivities.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
                
                // Load restaurant data for activities
                loadRestaurantsForActivities(recentActivities);
                
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading activity feed", e);
                showEmptyActivityState();
            });
    }
    
    private void setupActivityListener(String userId) {
        // Remove existing listener if any
        if (activityListener != null) {
            activityListener.remove();
        }
        
        // Set up real-time listener for reviews by current user so that we can update the activity feed in real time
        activityListener = db.collection("reviews")
            .whereEqualTo("userId", userId)
            .addSnapshotListener((queryDocumentSnapshots, error) -> {
                if (error != null) {
                    Log.e(TAG, "Error in activity listener", error);
                    return;
                }
                
                if (queryDocumentSnapshots != null) {
                    Log.d(TAG, "Activity listener triggered with " + queryDocumentSnapshots.getDocumentChanges().size() + " changes");
                    // Process changes and append new activities
                    processActivityChanges(queryDocumentSnapshots, userId);
                }
            });
    }
    
    private void processActivityChanges(com.google.firebase.firestore.QuerySnapshot queryDocumentSnapshots, String userId) {
        for (com.google.firebase.firestore.DocumentChange change : queryDocumentSnapshots.getDocumentChanges()) {
            if (change.getType() == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {
                try {
                    Review review = change.getDocument().toObject(Review.class);
                    review.setId(change.getDocument().getId());
                    
                    Log.d(TAG, "Processing modified review: " + review.getId() + " with " + (review.getVotes() != null ? review.getVotes().size() : 0) + " votes");
                    
                    // update review in map
                    reviewMap.put(review.getId(), review);
                    
                    // Check for any new votes
                    if (review.getVotes() != null) {
                        for (Map.Entry<String, Map<String, Object>> voteEntry : review.getVotes().entrySet()) {
                            String voterId = voteEntry.getKey();
                            Map<String, Object> voteData = voteEntry.getValue();
                            Boolean voteType = (Boolean) voteData.get("accurate");
                            java.util.Date voteTimestamp = null;
                            Object timestampObj = voteData.get("timestamp");
                            if (timestampObj instanceof com.google.firebase.Timestamp) {
                                voteTimestamp = ((com.google.firebase.Timestamp) timestampObj).toDate();
                            } else if (timestampObj instanceof java.util.Date) {
                                voteTimestamp = (java.util.Date) timestampObj;
                            }
                            
                            // Skip if the review author is voting on their own review
                            // Only show accurate votes
                            if (!voterId.equals(userId) && voteType != null && voteType) {
                                // Check if this vote activity already exists
                                boolean voteExists = false;
                                for (ActivityItem existingActivity : activities) {
                                    if (existingActivity.getType() == ActivityItem.ActivityType.VOTE &&
                                        existingActivity.getReviewId().equals(review.getId()) &&
                                        existingActivity.getUserId().equals(voterId)) {
                                        voteExists = true;
                                        break;
                                    }
                                }
                                
                                if (!voteExists) {
                                    Log.d(TAG, "Creating new vote activity for user: " + voterId + " on review: " + review.getId());
                                    // Create new vote activity
                                    ActivityItem newActivity = new ActivityItem(
                                        ActivityItem.ActivityType.VOTE,
                                        voterId,
                                        null, // Will be filled later
                                        null, // Will be filled later
                                        review.getId(),
                                        review.getCaption(),
                                        review.getRestaurantName(),
                                        voteTimestamp != null ? voteTimestamp : new java.util.Date()
                                    );
                                    newActivity.setVoteType(voteType);
                                    if (review.getImageUrls() != null && !review.getImageUrls().isEmpty()) {
                                        newActivity.setReviewFirstImageUrl(review.getImageUrls().get(0));
                                    }
                                    
                                    // Append to activities list
                                    appendNewActivity(newActivity);
                                }
                            }
                        }
                    }
                    
                    // Check for new comments
                    if (review.getComments() != null) {
                        for (com.example.food.data.Comment comment : review.getComments()) {
                            // Skip if the review author is commenting on their own review
                            if (!comment.getUserId().equals(userId)) {
                                // Check if this comment activity already exists
                                boolean commentExists = false;
                                for (ActivityItem existingActivity : activities) {
                                    if (existingActivity.getType() == ActivityItem.ActivityType.COMMENT &&
                                        existingActivity.getReviewId().equals(review.getId()) &&
                                        existingActivity.getUserId().equals(comment.getUserId()) &&
                                        existingActivity.getCommentText() != null &&
                                        existingActivity.getCommentText().equals(comment.getText())) {
                                        commentExists = true;
                                        break;
                                    }
                                }
                                
                                if (!commentExists) {
                                    // Create new comment activity
                                    ActivityItem newActivity = new ActivityItem(
                                        ActivityItem.ActivityType.COMMENT,
                                        comment.getUserId(),
                                        null, // Will be filled later name
                                        null, // Will be filled later avatar url
                                        review.getId(),
                                        review.getCaption(),
                                        review.getRestaurantName(),
                                        comment.getCreatedAt()
                                    );
                                    newActivity.setCommentText(comment.getText());
                                    if (review.getImageUrls() != null && !review.getImageUrls().isEmpty()) {
                                        newActivity.setReviewFirstImageUrl(review.getImageUrls().get(0));
                                    }
                                    
                                    // Append to activities list
                                    appendNewActivity(newActivity);
                                }
                            }
                        }
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error processing activity change", e);
                }
            }
        }
    }
    
    private void appendNewActivity(ActivityItem newActivity) {
        // Add to activities list
        activities.add(0, newActivity); // Add to beginning for newest first
        
        // Sort activities by timestamp
        activities.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        
        // Update adapter
        activityAdapter.setActivities(activities);
        activityAdapter.setReviewMap(reviewMap);
        activityAdapter.setRestaurantMap(restaurantMap);
        
        // Load user details for the new activity
        loadUserDetailsForNewActivity(newActivity);
        
        // Load restaurant details for the new activity
        loadRestaurantDetailsForNewActivity(newActivity);
        
        // Hide empty state if it was showing
        hideEmptyActivityState();
    }
    
    private void loadUserDetailsForNewActivity(ActivityItem activity) {
        db.collection("users").document(activity.getUserId())
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String userName = documentSnapshot.getString("name");
                    String userAvatarUrl = documentSnapshot.getString("profilePictureUrl");
                    
                    if (userName != null) {
                        activity.setUserName(userName);
                    }
                    if (userAvatarUrl != null) {
                        activity.setUserAvatarUrl(userAvatarUrl);
                    }
                    
                    // Update the adapter to reflect the new user details
                    activityAdapter.notifyDataSetChanged();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error fetching user details for new activity", e);
            });
    }
    
    private void loadRestaurantDetailsForNewActivity(ActivityItem activity) {
        Review review = reviewMap.get(activity.getReviewId());
        if (review == null || review.getRestaurantId() == null) {
            return;
        }
        
        String restaurantId = review.getRestaurantId();
        
        // Check if restaurant is already loaded
        if (restaurantMap.containsKey(restaurantId)) {
            Restaurant restaurant = restaurantMap.get(restaurantId);
            if (restaurant != null) {
                activity.setRestaurantName(restaurant.getName());
                activityAdapter.notifyDataSetChanged();
            }
            return;
        }
        
        // Load restaurant data
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
                            
                            // Update the activity with restaurant name
                            activity.setRestaurantName(restaurant.getName());
                            
                            // Update the adapter to reflect the new restaurant details
                            activityAdapter.notifyDataSetChanged();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing restaurant document for new activity", e);
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error fetching restaurant details for new activity", e);
            });
    }

    private void loadRestaurantsForActivities(List<ActivityItem> recentActivities) {
        if (recentActivities.isEmpty()) {
            showEmptyActivityState();
            return;
        }
        
        // Get unique restaurant IDs from reviews
        java.util.Set<String> restaurantIds = new java.util.HashSet<>();
        for (ActivityItem activity : recentActivities) {
            Review review = reviewMap.get(activity.getReviewId());
            if (review != null && review.getRestaurantId() != null) {
                restaurantIds.add(review.getRestaurantId());
            }
        }
        
        if (restaurantIds.isEmpty()) {
            fetchUserDetailsForActivities(recentActivities);
            return;
        }
        
        // Load restaurant data
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
                                
                                // Update activities with restaurant names
                                for (ActivityItem activity : recentActivities) {
                                    Review review = reviewMap.get(activity.getReviewId());
                                    if (review != null && restaurantId.equals(review.getRestaurantId())) {
                                        activity.setRestaurantName(restaurant.getName());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing restaurant document", e);
                        }
                    }
                    
                    // Check if all restaurants are loaded
                    boolean allRestaurantsLoaded = true;
                    for (String id : restaurantIds) {
                        if (!restaurantMap.containsKey(id)) {
                            allRestaurantsLoaded = false;
                            break;
                        }
                    }
                    
                    if (allRestaurantsLoaded) {
                        fetchUserDetailsForActivities(recentActivities);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading restaurant: " + restaurantId, e);
                    fetchUserDetailsForActivities(recentActivities);
                });
        }
    }

    private void fetchUserDetailsForActivities(List<ActivityItem> recentActivities) {
        if (recentActivities.isEmpty()) {
            showEmptyActivityState();
            return;
        }
        
        // Get unique user IDs
        java.util.Set<String> userIds = new java.util.HashSet<>();
        for (ActivityItem activity : recentActivities) {
            userIds.add(activity.getUserId());
        }
        
        // Fetch user details for each unique user
        for (String userId : userIds) {
            db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String userName = documentSnapshot.getString("name");
                        String userAvatarUrl = documentSnapshot.getString("avatarUrl");
                        
                        // Update all activities for this user
                        for (ActivityItem activity : recentActivities) {
                            if (activity.getUserId().equals(userId)) {
                                activity.setUserName(userName);
                                activity.setUserAvatarUrl(userAvatarUrl);
                            }
                        }
                        
                        // Check if all user details are loaded
                        boolean allLoaded = true;
                        for (ActivityItem activity : recentActivities) {
                            if (activity.getUserName() == null) {
                                allLoaded = false;
                                break;
                            }
                        }
                        
                        if (allLoaded) {
                            activities.clear();
                            activities.addAll(recentActivities);
                            activityAdapter.setActivities(activities);
                            activityAdapter.setReviewMap(reviewMap);
                            activityAdapter.setRestaurantMap(restaurantMap);
                            
                            if (activities.isEmpty()) {
                                showEmptyActivityState();
                            } else {
                                hideEmptyActivityState();
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user details for activity", e);
                });
        }
    }

    private void showEmptyActivityState() {
        if (emptyActivityLayout != null) {
            emptyActivityLayout.setVisibility(View.VISIBLE);
        }
        if (rvActivity != null) {
            rvActivity.setVisibility(View.GONE);
        }
    }

    private void hideEmptyActivityState() {
        if (emptyActivityLayout != null) {
            emptyActivityLayout.setVisibility(View.GONE);
        }
        if (rvActivity != null) {
            rvActivity.setVisibility(View.VISIBLE);
        }
    }

    private void loadFreshAnalyticsData() {
        if (auth.getCurrentUser() == null) {
            return;
        }
        
        // Fetch fresh user data from Firestore
        String userId = auth.getCurrentUser().getUid();
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot != null && documentSnapshot.exists()) {
                    UserProfile freshProfile = documentSnapshot.toObject(UserProfile.class);

                    if (freshProfile != null) {
                        
                        userProfile = freshProfile;
                        cacheManager.cacheUserProfile(freshProfile);



                        // Load analytics with fresh data
                        loadAnalyticsData();
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading fresh analytics data", e);

                // fallback to cached data if fresh data fails
              
                loadAnalyticsData();
            });
    }

    private void loadAnalyticsData() {
        if (userProfile == null || userProfile.getStats() == null) {
            return;
        }
        
        Map<String, Object> stats = userProfile.getStats();
        View view = getView();
        if (view == null) return;
        
        // Update main stats
        TextView tvTotalReviews = view.findViewById(R.id.tvTotalReviews);
        TextView tvAverageRating = view.findViewById(R.id.tvAverageRating);
        TextView tvTotalRestaurants = view.findViewById(R.id.tvTotalRestaurants);
        TextView tvTotalVotes = view.findViewById(R.id.tvTotalVotes);
        TextView tvMemberSinceStat = view.findViewById(R.id.tvMemberSinceStat);
        
        if (tvTotalReviews != null) {
            Object totalReviews = stats.get("totalReviews");
            tvTotalReviews.setText(totalReviews != null ? totalReviews.toString() : "0");
        }
        
        if (tvTotalRestaurants != null) {
            Object uniqueRestaurants = stats.get("uniqueRestaurants");
            tvTotalRestaurants.setText(uniqueRestaurants != null ? uniqueRestaurants.toString() : "0");
        }
        
        if (tvTotalVotes != null) {
            Object totalVotes = stats.get("totalVotes");
            tvTotalVotes.setText(totalVotes != null ? totalVotes.toString() : "0");
        }
        
        if (tvAverageRating != null) {
            Object avgAccuracy = stats.get("avgAccuracyPercent");
            if (avgAccuracy != null) {
                tvAverageRating.setText(String.format("%.0f%%", ((Number) avgAccuracy).doubleValue()));
            } else {
                tvAverageRating.setText("0%");
            }
        }
        
        if (tvMemberSinceStat != null) {
            long createdAt = userProfile.getCreatedAt();
            if (createdAt > 0) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault());
                String memberSince = sdf.format(new java.util.Date(createdAt));
                tvMemberSinceStat.setText(memberSince);
            } else {
                tvMemberSinceStat.setText("Unknown");
            }
        }
        
        // Update member duration
        TextView tvMemberDuration = view.findViewById(R.id.tvMemberDuration);
        if (tvMemberDuration != null) {
            long createdAt = userProfile.getCreatedAt();
            if (createdAt > 0) {
                long now = System.currentTimeMillis();
                long diffInMillis = now - createdAt;
                
                long days = diffInMillis / (24 * 60 * 60 * 1000);
                long months = days / 30;
                long years = days / 365;
                
                String duration;
                if (years > 0) {
                    duration = years == 1 ? "1 year" : years + " years";
                } else if (months > 0) {
                    duration = months == 1 ? "1 month" : months + " months";
                } else {
                    duration = days == 1 ? "1 day" : days + " days";
                }
                
                tvMemberDuration.setText("Member for " + duration);
            } else {
                tvMemberDuration.setText("Member for unknown duration");
            }
        }
        
        
        
        // Update cuisine diversity if we have review data
        updateCuisineDiversity();
    }
    
    private void updateCuisineDiversity() {
        View view = getView();
        if (view == null) return;
        
        LinearLayout cuisineDiversityContainer = view.findViewById(R.id.cuisineDiversityContainer);
        TextView tvCategoriesCount = view.findViewById(R.id.tvCategoriesCount);
        TextView tvRegionsCount = view.findViewById(R.id.tvRegionsCount);
        LinearLayout categoriesSection = view.findViewById(R.id.categoriesDropdown);
        LinearLayout regionsSection = view.findViewById(R.id.regionsDropdown);
        ImageView ivCategoriesArrow = view.findViewById(R.id.ivCategoriesArrow);
        ImageView ivRegionsArrow = view.findViewById(R.id.ivRegionsArrow);
        RecyclerView rvCategories = view.findViewById(R.id.rvCategories);
        RecyclerView rvRegions = view.findViewById(R.id.rvRegions);
        
        if (cuisineDiversityContainer == null || tvCategoriesCount == null || tvRegionsCount == null ||
            categoriesSection == null || regionsSection == null || ivCategoriesArrow == null ||
            ivRegionsArrow == null || rvCategories == null || rvRegions == null) return;
        
        
        if (userProfile != null && userProfile.getStats() != null) {
            Map<String, Object> stats = userProfile.getStats();
            Object uniqueCategories = stats.get("uniqueCategories");
            Object uniqueRegions = stats.get("uniqueRegions");
            
            int categoriesCount = uniqueCategories != null ? ((Number) uniqueCategories).intValue() : 0;
            int regionsCount = uniqueRegions != null ? ((Number) uniqueRegions).intValue() : 0;
            
            if (categoriesCount > 0 || regionsCount > 0) {
                // Set counts from fresh stats
                tvCategoriesCount.setText(String.valueOf(categoriesCount));
                tvRegionsCount.setText(String.valueOf(regionsCount));
                
                
                setupDropdown(rvCategories, new ArrayList<>(), ivCategoriesArrow, categoriesSection);
                setupDropdown(rvRegions, new ArrayList<>(), ivRegionsArrow, regionsSection);
        
        // Setup click listeners for dropdown sections
        LinearLayout categoriesClickSection = view.findViewById(R.id.categoriesSection);
        LinearLayout regionsClickSection = view.findViewById(R.id.regionsSection);
        
        if (categoriesClickSection != null) {
            categoriesClickSection.setOnClickListener(v -> toggleDropdown(categoriesSection, ivCategoriesArrow));
        }
        
        if (regionsClickSection != null) {
            regionsClickSection.setOnClickListener(v -> toggleDropdown(regionsSection, ivRegionsArrow));
        }
                
                cuisineDiversityContainer.setVisibility(View.VISIBLE);
            } else {
                cuisineDiversityContainer.setVisibility(View.GONE);
            }
        } else {
            cuisineDiversityContainer.setVisibility(View.GONE);
        }
    }
    
    private void setupDropdown(RecyclerView recyclerView, List<String> items, ImageView arrow, LinearLayout dropdown) {
        // Setup RecyclerView
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        
        // Create adapter
        androidx.recyclerview.widget.RecyclerView.Adapter adapter = new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dropdown_item, parent, false);
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {};
            }
            
            @Override
            public void onBindViewHolder(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                TextView textView = (TextView) holder.itemView;
                textView.setText(items.get(position));
            }
            
            @Override
            public int getItemCount() {
                return items.size();
            }
        };
        
        recyclerView.setAdapter(adapter);
        
        
    }
    
    private void toggleDropdown(LinearLayout dropdown, ImageView arrow) {
        if (dropdown.getVisibility() == View.VISIBLE) {
            dropdown.setVisibility(View.GONE);
            arrow.setRotation(0);
        } else {
            dropdown.setVisibility(View.VISIBLE);
            arrow.setRotation(180);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (profileListener != null) {
            profileListener.remove();
        }
        if (activityListener != null) {
            activityListener.remove();
        }
    }
}



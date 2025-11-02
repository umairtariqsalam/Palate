package com.example.food;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.food.adapters.ReviewWidgetAdapter;
import com.example.food.data.Review;
import com.example.food.dialogs.ReviewDetailsDialog;
import com.example.food.model.Restaurant;
import com.example.food.service.ReviewService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    
    private RecyclerView rvReviews;
    private ReviewWidgetAdapter reviewAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private EditText etSearch;
    private LinearLayout layoutEmptyState;
    
    private ReviewService reviewService;
    private List<Review> allReviews;
    private Map<String, Restaurant> restaurantMap;
    private Map<String, String> userNamesMap; // Cache for user names
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        initViews(view);
        setupRecyclerView();
        setupSwipeRefresh();
        setupSearch();
        
        reviewService = new ReviewService();
        allReviews = new ArrayList<>();
        restaurantMap = new HashMap<>();
        userNamesMap = new HashMap<>();
        db = FirebaseFirestore.getInstance();
        
        loadReviews();
        
        return view;
    }

    private void initViews(View view) {
        rvReviews = view.findViewById(R.id.rv_posts);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        etSearch = view.findViewById(R.id.et_search);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
    }

    private void setupRecyclerView() {
        reviewAdapter = new ReviewWidgetAdapter(allReviews, new ReviewWidgetAdapter.OnReviewClickListener() {
            @Override
            public void onReviewClick(Review review, Restaurant restaurant) {
                // Open review details dialog
                ReviewDetailsDialog dialog = new ReviewDetailsDialog(getActivity(), review, restaurant);
                dialog.setOnReviewUpdatedListener(updatedReview -> {
                    // Find and update the review in the list
                    for (int i = 0; i < allReviews.size(); i++) {
                        if (allReviews.get(i).getId().equals(updatedReview.getId())) {
                            allReviews.set(i, updatedReview);
                            reviewAdapter.notifyItemChanged(i);
                            break;
                        }
                    }
                });
                dialog.show();
            }
            
            @Override
            public void onUserClick(String userId) {
                FirebaseAuth auth = FirebaseAuth.getInstance();
                if (auth.getCurrentUser() == null) return;
                
                if (getActivity() != null && getActivity() instanceof MainActivity) {
                    if (userId.equals(auth.getCurrentUser().getUid())) {
                        // navigate to own profile using bottom nav
                        BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_nav);
                        if (bottomNav != null) {
                            bottomNav.setSelectedItemId(R.id.nav_profile);
                        }
                    } else {
                        // view someone else's profile
                        ProfileFragment profileFragment = ProfileFragment.newInstance(userId);
                        getActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, profileFragment)
                            .addToBackStack(null)
                            .commit();
                    }
                }
            }
        });
        
        // Set up staggered grid layout
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        rvReviews.setLayoutManager(layoutManager);
        
        // add spacing between items matching profile page
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
        
        // Set restaurant map to adapter
        reviewAdapter.setRestaurantMap(restaurantMap);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(R.color.primary_gradient_start);
        swipeRefreshLayout.setOnRefreshListener(this::refreshReviews);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                filterReviews(query);
            }
        });
    }


    private void loadReviews() {
        showLoading(true);
        
        reviewService.loadReviews(new ReviewService.ReviewsLoadCallback() {
            @Override
            public void onSuccess(List<Review> reviews) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    allReviews.clear();
                    allReviews.addAll(reviews);
                    loadUserInfoForReviews(reviews);
                    loadRestaurants();
                    showLoading(false);
                });
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    showLoading(false);
                    showError("Failed to load reviews: " + e.getMessage());
                    Log.e(TAG, "Error loading reviews", e);
                });
            }
        });
    }

    private void refreshReviews() {
        reviewService.loadReviews(new ReviewService.ReviewsLoadCallback() {
            @Override
            public void onSuccess(List<Review> reviews) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    allReviews.clear();
                    allReviews.addAll(reviews);
                    loadUserNames();
                    loadRestaurants();
                    swipeRefreshLayout.setRefreshing(false);
                });
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    showError("Failed to refresh reviews");
                    Log.e(TAG, "Error refreshing reviews", e);
                });
            }
        });
    }

    private void loadUserNames() {
        if (allReviews.isEmpty()) {
            return;
        }

        List<String> userIds = new ArrayList<>();
        for (Review review : allReviews) {
            if (review.getUserId() != null && !userIds.contains(review.getUserId())) {
                userIds.add(review.getUserId());
            }
        }

        if (userIds.isEmpty()) {
            return;
        }

        int batchSize = 10;
        for (int i = 0; i < userIds.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, userIds.size());
            List<String> batch = userIds.subList(i, endIndex);
            
            db.collection("users")
                .whereIn("__name__", batch)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String userId = document.getId();
                        String userName = document.getString("name");
                        if (userName == null || userName.trim().isEmpty()) {
                            userName = document.getString("username");
                        }
                        if (userName != null && !userName.trim().isEmpty()) {
                            userNamesMap.put(userId, userName);
                            for (Review review : allReviews) {
                                if (review.getUserId().equals(userId)) {
                                    review.setUserName(userName);
                                }
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user batch", e);
                });
        }
    }

    private void loadRestaurants() {
        if (allReviews.isEmpty()) {
            updateUI();
            return;
        }

        List<String> restaurantIds = new ArrayList<>();
        for (Review review : allReviews) {
            if (review.getRestaurantId() != null && !restaurantIds.contains(review.getRestaurantId())) {
                restaurantIds.add(review.getRestaurantId());
            }
        }

        if (restaurantIds.isEmpty()) {
            updateUI();
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
                    updateUI();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading restaurant: " + restaurantId, e);
                    updateUI();
                });
        }
    }

    private void filterReviews(String query) {
        if (reviewAdapter != null) {
            // Enhanced filtering based on review content, user names, and restaurant names
            List<Review> filteredReviews = new ArrayList<>();
            if (query == null || query.trim().isEmpty()) {
                filteredReviews.addAll(allReviews);
            } else {
                String lowerCaseQuery = query.toLowerCase().trim();
                for (Review review : allReviews) {
                    boolean matches = false;
                    
                    // Search in description
                    if (review.getDescription() != null && review.getDescription().toLowerCase().contains(lowerCaseQuery)) {
                        matches = true;
                    }
                    
                    // Search in caption
                    if (!matches && review.getCaption() != null && review.getCaption().toLowerCase().contains(lowerCaseQuery)) {
                        matches = true;
                    }
                    
                    // Search in restaurant name
                    if (!matches && review.getRestaurantName() != null && review.getRestaurantName().toLowerCase().contains(lowerCaseQuery)) {
                        matches = true;
                    }
                    
                    // Search in user name
                    if (!matches && review.getUserName() != null && review.getUserName().toLowerCase().contains(lowerCaseQuery)) {
                        matches = true;
                    }
                    
                    if (matches) {
                        filteredReviews.add(review);
                    }
                }
            }
            reviewAdapter.setReviews(filteredReviews);
            updateEmptyState();
        }
    }

    private void updateUI() {
        if (reviewAdapter != null) {
            reviewAdapter.setReviews(allReviews);
            reviewAdapter.setRestaurantMap(restaurantMap);
            updateEmptyState();
        }
    }

    private void updateEmptyState() {
        if (reviewAdapter != null && layoutEmptyState != null && rvReviews != null) {
            if (reviewAdapter.getItemCount() == 0) {
                layoutEmptyState.setVisibility(View.VISIBLE);
                rvReviews.setVisibility(View.GONE);
            } else {
                layoutEmptyState.setVisibility(View.GONE);
                rvReviews.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showLoading(boolean show) {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(show);
        }
    }

    private void showError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }
    
    private void loadUserInfoForReviews(List<Review> reviews) {
        if (reviews.isEmpty()) return;
        
        // Get unique user IDs
        java.util.Set<String> userIds = new java.util.HashSet<>();
        for (Review review : reviews) {
            if (review.getUserId() != null) {
                userIds.add(review.getUserId());
            }
        }
        
        // Load user information for each unique user
        for (String userId : userIds) {
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String userName = documentSnapshot.getString("name");
                        String userAvatarUrl = documentSnapshot.getString("avatarUrl");
                        
                        // Update all reviews for this user
                        for (Review review : allReviews) {
                            if (userId.equals(review.getUserId())) {
                                if (userName != null) {
                                    review.setUserName(userName);
                                }
                                if (userAvatarUrl != null) {
                                    review.setUserAvatarUrl(userAvatarUrl);
                                }
                            }
                        }
                        
                        // Notify adapter of changes
                        if (reviewAdapter != null) {
                            reviewAdapter.notifyDataSetChanged();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user info for user: " + userId, e);
                });
        }
    }
}


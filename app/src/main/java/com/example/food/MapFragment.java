package com.example.food;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.food.adapters.ReviewWidgetAdapter;
import com.example.food.data.Review;
import com.example.food.data.CrowdFeedback;
import com.example.food.dialogs.ReviewDetailsDialog;
import com.example.food.service.CrowdDensityService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.example.food.model.Restaurant;
// FirebaseDataUploader removed, no longer need upload functionality

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "MapFragment";
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private PlacesClient placesClient;
    private FirebaseFirestore db;
    private CrowdDensityService crowdDensityService;
    private FirebaseAuth mAuth;
    
    // Map control buttons
    private ImageButton btnZoomIn;
    private ImageButton btnZoomOut;
    private ImageButton btnMyLocation;

    // High-rated restaurant data
    private List<Restaurant> highRatedRestaurants;
    
    // Restaurant search
    private AutoCompleteTextView etRestaurantSearch;
    private List<Restaurant> restaurants;
    
    // Store markers with restaurant IDs for color updates
    private Map<String, Marker> restaurantMarkers;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fine = Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false));
                boolean coarse = Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false));
                if (fine || coarse) {
                    enableMyLocationAndLoad();
                } else {
                    android.content.Context context = getContext();
                    if (context != null && isAdded()) {
                        Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize map control buttons
        btnZoomIn = view.findViewById(R.id.btn_zoom_in);
        btnZoomOut = view.findViewById(R.id.btn_zoom_out);
        btnMyLocation = view.findViewById(R.id.btn_my_location);
        
        // Set button click listeners
        btnZoomIn.setOnClickListener(v -> zoomIn());
        btnZoomOut.setOnClickListener(v -> zoomOut());
        btnMyLocation.setOnClickListener(v -> moveToMyLocation());
        
        // Initialize restaurant search
        etRestaurantSearch = view.findViewById(R.id.et_restaurant_search);
        restaurants = new ArrayList<>();
        restaurantMarkers = new HashMap<>();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());

        if (!Places.isInitialized()) {
            Places.initialize(requireContext().getApplicationContext(), getString(R.string.google_maps_key));
        }
        placesClient = Places.createClient(requireContext());
        
        // Initialize Firebase Firestore
        db = FirebaseFirestore.getInstance();
        crowdDensityService = new CrowdDensityService();
        mAuth = FirebaseAuth.getInstance();

        // Simplified map initialization
        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getChildFragmentManager().beginTransaction()
                .replace(R.id.map_container, mapFragment)
                .commit();
        mapFragment.getMapAsync(this);
        
        // Load restaurants for search
        loadRestaurants();

        // if special restaurant id argument then open details as soon as ready
        Bundle args = getArguments();
        if (args != null && args.containsKey("open_restaurant_id")) {
            String targetRestaurantId = args.getString("open_restaurant_id");
            if (targetRestaurantId != null) {
                // Fetch from Firestore and show bottom sheet
                db.collection("restaurants").document(targetRestaurantId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Restaurant restaurant = doc.toObject(Restaurant.class);
                            if (restaurant != null) {
                                restaurant.setId(doc.getId());
                                showRestaurantPostsBottomSheet(restaurant);
                            }
                        }
                    });
                // Remove the argument so it doesnt repeat
                args.remove("open_restaurant_id");
            }
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setMapToolbarEnabled(false);
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        
        // Set marker click listener
        googleMap.setOnMarkerClickListener(marker -> {
            Restaurant restaurant = (Restaurant) marker.getTag();
            if (restaurant != null) {
                showRestaurantPostsBottomSheet(restaurant);
            }
            return true;
        });
        
        enableMyLocationAndLoad();
    }

    private void enableMyLocationAndLoad() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (googleMap != null) {
                try { googleMap.setMyLocationEnabled(true); } catch (SecurityException ignored) {}
            }
        } else {
            permissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        }
        
        // Show map and restaurant markers regardless of location permission
        moveToMelbourne();
        addRestaurantMarkers();
    }
    
    // Upload functionality removed, now only load data from Firebase

    private void moveToMelbourne() {
        // Melbourne city center coordinates (more precise coordinates)
        LatLng melbourne = new LatLng(-37.810272, 144.962646);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(melbourne, 10f));
    }
    
    // Load restaurant data from Firebase and add markers
    private void addRestaurantMarkers() {
        if (googleMap == null) {
            return;
        }
        
        Log.d(TAG, "Starting to load restaurant data from Firebase...");
        
        db.collection("restaurants")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Firebase connection successful, document count: " + queryDocumentSnapshots.size());
                    
                    if (!isAdded() || getContext() == null) {
                        return;
                    }
                    
                    if (queryDocumentSnapshots.isEmpty()) {
                        // No data in Firebase
                        Log.d(TAG, "No restaurant data in Firebase");
                    } else {
                        // Data exists in Firebase, load and display
                        List<Restaurant> restaurantsList = new ArrayList<>();
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            try {
                                Restaurant restaurant = document.toObject(Restaurant.class);
                                restaurant.setId(document.getId());
                                restaurantsList.add(restaurant);
                                
                                // Add map marker with default green color (will be updated based on crowd density)
                                if (googleMap != null) {
                                    LatLng position = new LatLng(restaurant.getLatitude(), restaurant.getLongitude());
                                    MarkerOptions markerOptions = new MarkerOptions()
                                            .position(position)
                                            .title(restaurant.getName())
                                            .snippet(restaurant.getAddress())
                                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                                    
                                    Marker marker = googleMap.addMarker(markerOptions);
                                    if (marker != null) {
                                        marker.setTag(restaurant);
                                        restaurantMarkers.put(restaurant.getId(), marker);
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to parse restaurant data: " + document.getId(), e);
                            }
                        }
                        
                        // Load crowd density for each restaurant and update marker colors
                        for (Restaurant restaurant : restaurantsList) {
                            loadCrowdDensityForMarker(restaurant.getId());
                        }
                        
                        Log.d(TAG, "Successfully loaded " + restaurantsList.size() + " restaurants");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firebase loading failed", e);
                });
    }

    /**
     * Show bottom sheet with posts related to the restaurant
     */
    private void showRestaurantPostsBottomSheet(Restaurant restaurant) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        BottomSheetDialog bottomSheet = new BottomSheetDialog(getContext());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_restaurant_posts, null);
        bottomSheet.setContentView(view);

        // Initialize views
        TextView tvRestaurantName = view.findViewById(R.id.tv_restaurant_name);
        TextView tvRestaurantAddress = view.findViewById(R.id.tv_restaurant_address);
        TextView tvPostsCount = view.findViewById(R.id.tv_posts_count);
        RecyclerView rvPosts = view.findViewById(R.id.rv_posts);
        TextView tvNoPosts = view.findViewById(R.id.tv_no_posts);
        androidx.appcompat.widget.AppCompatButton btnNavigate = view.findViewById(R.id.btn_navigate);
        ImageView btnClose = view.findViewById(R.id.btn_close);
        
        // Crowd density views
        LinearLayout llCrowdDensity = view.findViewById(R.id.ll_crowd_density);
        View vCrowdIndicator = view.findViewById(R.id.v_crowd_indicator);
        TextView tvCrowdStatus = view.findViewById(R.id.tv_crowd_status);
        TextView tvCrowdDescription = view.findViewById(R.id.tv_crowd_description);
        TextView tvFeedbackCount = view.findViewById(R.id.tv_feedback_count);
        
        // Feedback buttons
        LinearLayout llFeedbackButtons = view.findViewById(R.id.ll_feedback_buttons);
        Button btnNotCrowded = view.findViewById(R.id.btn_not_crowded);
        Button btnModeratelyCrowded = view.findViewById(R.id.btn_moderately_crowded);
        Button btnVeryCrowded = view.findViewById(R.id.btn_very_crowded);

        // Set restaurant info
        tvRestaurantName.setText(restaurant.getName());
        tvRestaurantAddress.setText(restaurant.getAddress());

        // Load and display crowd density
        loadCrowdDensity(restaurant.getId(), vCrowdIndicator, tvCrowdStatus, tvCrowdDescription, tvFeedbackCount);

        // Set up feedback button click listeners with refresh functionality
        btnNotCrowded.setOnClickListener(v -> submitCrowdFeedback(restaurant.getId(), 1, vCrowdIndicator, tvCrowdStatus, tvCrowdDescription, tvFeedbackCount));
        btnModeratelyCrowded.setOnClickListener(v -> submitCrowdFeedback(restaurant.getId(), 2, vCrowdIndicator, tvCrowdStatus, tvCrowdDescription, tvFeedbackCount));
        btnVeryCrowded.setOnClickListener(v -> submitCrowdFeedback(restaurant.getId(), 3, vCrowdIndicator, tvCrowdStatus, tvCrowdDescription, tvFeedbackCount));

        // Set up navigation button click listener
        btnNavigate.setOnClickListener(v -> {
            navigateToRestaurant(restaurant);
            bottomSheet.dismiss();
        });
        
        // Set up close button click listener
        btnClose.setOnClickListener(v -> bottomSheet.dismiss());

        // Setup RecyclerView
        List<Review> reviews = new ArrayList<>();
        final ReviewWidgetAdapter[] adapterHolder = new ReviewWidgetAdapter[1];
        ReviewWidgetAdapter adapter = new ReviewWidgetAdapter(reviews, new ReviewWidgetAdapter.OnReviewClickListener() {
            @Override
            public void onReviewClick(Review review, Restaurant restaurantData) {
                // Open review details dialog
                android.content.Context context = getContext();
                if (context != null && isAdded()) {
                    ReviewDetailsDialog dialog = new ReviewDetailsDialog(context, review, restaurant);
                    dialog.setOnReviewUpdatedListener(updatedReview -> {
                        // Update the review in the adapter's list
                        for (int i = 0; i < reviews.size(); i++) {
                            if (reviews.get(i).getId().equals(updatedReview.getId())) {
                                reviews.set(i, updatedReview);
                                if (adapterHolder[0] != null) {
                                    adapterHolder[0].notifyItemChanged(i);
                                }
                                break;
                            }
                        }
                    });
                    dialog.show();
                    bottomSheet.dismiss();
                }
            }
            
            @Override
            public void onUserClick(String userId) {
                FirebaseAuth auth = FirebaseAuth.getInstance();
                if (auth.getCurrentUser() == null) return;
                
                bottomSheet.dismiss();
                
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
        adapterHolder[0] = adapter;
        android.content.Context context = getContext();
        if (context != null) {
            // Use staggered grid layout same as home page
            androidx.recyclerview.widget.StaggeredGridLayoutManager layoutManager = 
                new androidx.recyclerview.widget.StaggeredGridLayoutManager(2, androidx.recyclerview.widget.StaggeredGridLayoutManager.VERTICAL);
            rvPosts.setLayoutManager(layoutManager);
            rvPosts.setAdapter(adapter);
        }

        // Load reviews for this restaurant
        loadRestaurantReviews(restaurant.getId(), adapter, rvPosts, tvNoPosts, tvPostsCount);

        bottomSheet.show();
    }

    /**
     * Load reviews for a specific restaurant
     */
    private void loadRestaurantReviews(String restaurantId, ReviewWidgetAdapter adapter, 
                                     RecyclerView rvPosts, TextView tvNoPosts, TextView tvPostsCount) {
        db.collection("reviews")
            .whereEqualTo("restaurantId", restaurantId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<Review> reviews = new ArrayList<>();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    Review review = document.toObject(Review.class);
                    review.setId(document.getId());
                    review.refreshAccuracyFromVotes(); // Calculate accuracy from votes
                    reviews.add(review);
                }

                // Sort by timestamp (newest first)
                reviews.sort((r1, r2) -> {
                    if (r1.getCreatedAt() == null) return 1;
                    if (r2.getCreatedAt() == null) return -1;
                    return r2.getCreatedAt().compareTo(r1.getCreatedAt());
                });

                adapter.setReviews(reviews);
                tvPostsCount.setText(String.valueOf(reviews.size()));

                if (reviews.isEmpty()) {
                    tvNoPosts.setVisibility(View.VISIBLE);
                    rvPosts.setVisibility(View.GONE);
                } else {
                    tvNoPosts.setVisibility(View.GONE);
                    rvPosts.setVisibility(View.VISIBLE);
                    // Load user info for reviews to properly display names and avatars
                    loadUserInfoForReviews(reviews, adapter);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading restaurant reviews", e);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), "Failed to load reviews", Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    // Load crowd density for marker and update color
    private void loadCrowdDensityForMarker(String restaurantId) {
        if (!isAdded() || googleMap == null) {
            return;
        }
        
        crowdDensityService.calculateCrowdDensity(restaurantId, new CrowdDensityService.CrowdDensityCallback() {
            @Override
            public void onSuccess(CrowdDensityService.CrowdDensityResult result) {
                // Update marker color on main thread
                android.app.Activity activity = getActivity();
                if (activity != null && isAdded()) {
                    activity.runOnUiThread(() -> {
                        if (!isAdded() || googleMap == null) {
                            return;
                        }
                        
                        Marker marker = restaurantMarkers.get(restaurantId);
                        if (marker != null) {
                            // Set marker color based on crowding level
                            float markerHue;
                            int crowdingLevel = result.getCrowdingLevel();
                            
                            // 3 = Very Crowded (red), 2 = Moderately Crowded (yellow), 1 = Not Crowded (green), 0 = No Data (green)
                            if (crowdingLevel == 3) {
                                markerHue = BitmapDescriptorFactory.HUE_RED;
                            } else if (crowdingLevel == 2) {
                                markerHue = BitmapDescriptorFactory.HUE_YELLOW;
                            } else {
                                // Level 1 (not crowded) or 0 (no data) = green
                                markerHue = BitmapDescriptorFactory.HUE_GREEN;
                            }
                            
                            marker.setIcon(BitmapDescriptorFactory.defaultMarker(markerHue));
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                // On error, keep default green color
                Log.e(TAG, "Error loading crowd density for marker: " + restaurantId, e);
            }
        });
    }
    
    // Load user info for reviews
    private void loadUserInfoForReviews(List<Review> reviews, ReviewWidgetAdapter adapter) {
        if (reviews.isEmpty() || !isAdded()) return;
        
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
                    if (documentSnapshot.exists() && isAdded()) {
                        String userName = documentSnapshot.getString("name");
                        if (userName == null || userName.trim().isEmpty()) {
                            userName = documentSnapshot.getString("username");
                        }
                        String userAvatarUrl = documentSnapshot.getString("avatarUrl");
                        
                        // Update all reviews for this user
                        for (Review review : reviews) {
                            if (userId.equals(review.getUserId())) {
                                if (userName != null && !userName.trim().isEmpty()) {
                                    review.setUserName(userName);
                                }
                                if (userAvatarUrl != null && !userAvatarUrl.trim().isEmpty()) {
                                    review.setUserAvatarUrl(userAvatarUrl);
                                }
                            }
                        }
                        
                        // Notify adapter of changes
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user info for user: " + userId, e);
                });
        }
    }
    
    // Zoom in functionality
    private void zoomIn() {
        if (googleMap != null) {
            googleMap.animateCamera(CameraUpdateFactory.zoomIn());
        }
    }
    
    // Zoom out functionality
    private void zoomOut() {
        if (googleMap != null) {
            googleMap.animateCamera(CameraUpdateFactory.zoomOut());
        }
    }
    
    // Move to my location
    private void moveToMyLocation() {
        if (googleMap == null || fusedLocationClient == null || !isAdded() || getContext() == null) {
            return;
        }
        
        android.content.Context context = getContext();
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null && googleMap != null && isAdded()) {
                        LatLng myLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 15f));
                    }
                });
            } catch (SecurityException e) {
                Log.e(TAG, "Error getting location", e);
            }
        } else {
            permissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }
    
    // Load restaurants for search
    private void loadRestaurants() {
        db.collection("restaurants")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!isAdded() || getContext() == null) {
                    return;
                }
                restaurants.clear();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    Restaurant restaurant = document.toObject(Restaurant.class);
                    if (restaurant != null) {
                        restaurant.setId(document.getId());
                        restaurants.add(restaurant);
                    }
                }
                setupRestaurantSearch();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading restaurants", e);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), "Failed to load restaurants", Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    // Setup restaurant search adapter
    private void setupRestaurantSearch() {
        if (!isAdded() || getContext() == null || etRestaurantSearch == null) {
            return;
        }
        
        List<String> restaurantNames = new ArrayList<>();
        for (Restaurant restaurant : restaurants) {
            restaurantNames.add(restaurant.getName());
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), 
            R.layout.simple_dropdown_item_white, restaurantNames);
        etRestaurantSearch.setAdapter(adapter);
        etRestaurantSearch.setDropDownBackgroundDrawable(ContextCompat.getDrawable(getContext(), R.drawable.rounded_background));
        
        // Handle restaurant selection
        etRestaurantSearch.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            Restaurant selectedRestaurant = findRestaurantByName(selectedName);
            if (selectedRestaurant != null) {
                navigateToRestaurantOnMap(selectedRestaurant);
            }
        });
    }
    
    // Find restaurant by name
    private Restaurant findRestaurantByName(String name) {
        for (Restaurant restaurant : restaurants) {
            if (restaurant.getName().equals(name)) {
                return restaurant;
            }
        }
        return null;
    }
    
    // Navigate to restaurant on map
    private void navigateToRestaurantOnMap(Restaurant restaurant) {
        if (googleMap == null) {
            return;
        }
        
        LatLng position = new LatLng(restaurant.getLatitude(), restaurant.getLongitude());
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f));
        
        // Show bottom sheet after a short delay to allow camera animation
        View view = getView();
        if (view != null) {
            view.postDelayed(() -> {
                if (isAdded()) {
                    showRestaurantPostsBottomSheet(restaurant);
                }
            }, 100);
        }
    }

    /**
     * Load and display crowd density for a restaurant
     */
    private void loadCrowdDensity(String restaurantId, View indicator, TextView status, 
                                  TextView description, TextView feedbackCount) {
        crowdDensityService.calculateCrowdDensity(restaurantId, new CrowdDensityService.CrowdDensityCallback() {
            @Override
            public void onSuccess(CrowdDensityService.CrowdDensityResult result) {
                // Update UI on main thread
                android.app.Activity activity = getActivity();
                if (activity != null && isAdded()) {
                    activity.runOnUiThread(() -> {
                        if (!isAdded() || getContext() == null) {
                            return;
                        }
                        // Set indicator color
                        int colorResId = result.getColorResId();
                        indicator.setBackgroundColor(ContextCompat.getColor(getContext(), colorResId));
                        
                        // Set status text
                        status.setText(result.getStatusText());
                        description.setText(result.getDescription());
                        
                        // Set feedback count
                        if (result.hasRecentData()) {
                            feedbackCount.setText(result.getFeedbackCount() + " feedbacks");
                        } else {
                            feedbackCount.setText("No recent data");
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error loading crowd density", e);
                android.app.Activity activity = getActivity();
                if (activity != null && isAdded()) {
                    activity.runOnUiThread(() -> {
                        if (isAdded()) {
                            status.setText("Error loading status");
                            description.setText("Please try again later");
                            feedbackCount.setText("Error");
                        }
                    });
                }
            }
        });
    }

    /**
     * Submit crowd feedback
     */
    private void submitCrowdFeedback(String restaurantId, int crowdingLevel, View indicator, 
                                   TextView status, TextView description, TextView feedbackCount) {
        // Get current user ID from Firebase Auth
        if (mAuth.getCurrentUser() == null) {
            android.content.Context context = getContext();
            if (context != null && isAdded()) {
                Toast.makeText(context, "Please login to submit feedback", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        String currentUserId = mAuth.getCurrentUser().getUid();
        
        crowdDensityService.submitFeedback(restaurantId, currentUserId, crowdingLevel, 
            new CrowdDensityService.FeedbackSubmitCallback() {
                @Override
                public void onSuccess() {
                    android.app.Activity activity = getActivity();
                    if (activity != null && isAdded()) {
                        activity.runOnUiThread(() -> {
                            android.content.Context context = getContext();
                            if (context != null && isAdded()) {
                                Toast.makeText(context, "Feedback submitted successfully!", Toast.LENGTH_SHORT).show();
                                // Refresh crowd density display after successful submission
                                loadCrowdDensity(restaurantId, indicator, status, description, feedbackCount);
                                // Refresh the map marker color
                                loadCrowdDensityForMarker(restaurantId);
                            }
                        });
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error submitting feedback", e);
                    android.app.Activity activity = getActivity();
                    if (activity != null && isAdded()) {
                        activity.runOnUiThread(() -> {
                            android.content.Context context = getContext();
                            if (context != null && isAdded()) {
                                String errorMessage = e.getMessage();
                                if (errorMessage != null && errorMessage.contains("already submitted")) {
                                    Toast.makeText(context, "Please wait before submitting another feedback", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(context, "Failed to submit feedback: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                }
            });
    }
    
    /**
     * Navigate to restaurant using Google Maps
     */
    private void navigateToRestaurant(Restaurant restaurant) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        try {
            // Create URI for Google Maps navigation
            String uri = String.format("google.navigation:q=%f,%f&mode=d", 
                restaurant.getLatitude(), restaurant.getLongitude());
            
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri));
            intent.setPackage("com.google.android.apps.maps");
            
            // Check if Google Maps is installed
            android.content.Context context = getContext();
            if (context != null && intent.resolveActivity(context.getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // Fallback to web browser if Google Maps app is not installed
                String webUri = String.format("https://www.google.com/maps/dir/?api=1&destination=%f,%f&travelmode=driving",
                    restaurant.getLatitude(), restaurant.getLongitude());
                Intent webIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(webUri));
                startActivity(webIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening navigation", e);
            android.content.Context context = getContext();
            if (context != null && isAdded()) {
                Toast.makeText(context, "Unable to open navigation, please check if Google Maps is installed", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    
    
    // Make status bar transparent
    private void makeStatusBarTransparent() {
        android.app.Activity activity = getActivity();
        if (activity != null) {
            Window window = activity.getWindow();
            if (window != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int flags = window.getDecorView().getSystemUiVisibility();
                    flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    window.getDecorView().setSystemUiVisibility(flags);
                }
            }
        }
    }
    
    // Restore default status bar
    private void restoreStatusBar() {
        android.app.Activity activity = getActivity();
        if (activity != null && getContext() != null) {
            Window window = activity.getWindow();
            if (window != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    window.setStatusBarColor(ContextCompat.getColor(getContext(), R.color.white));
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int flags = window.getDecorView().getSystemUiVisibility();
                    // Remove fullscreen layout flags
                    flags &= ~View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                    flags &= ~View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                    // Keep light status bar
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    window.getDecorView().setSystemUiVisibility(flags);
                }
            }
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        makeStatusBarTransparent();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Restore default status bar when leaving map
        restoreStatusBar();
    }
    
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            // Fragment is hidden, restore status bar
            restoreStatusBar();
        } else if (isAdded()) {
            // Fragment is visible, make status bar transparent
            makeStatusBarTransparent();
        }
    }
}
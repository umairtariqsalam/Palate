package com.example.food;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

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
import com.google.android.material.bottomsheet.BottomSheetDialog;
// FragmentManager and FragmentTransaction no longer needed, simplified map initialization

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
import java.util.List;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "MapFragment";
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private PlacesClient placesClient;
    private FirebaseFirestore db;
    private CrowdDensityService crowdDensityService;
    private FirebaseAuth mAuth;
    
    // Zoom in/out buttons
    private ImageButton btnZoomIn;
    private ImageButton btnZoomOut;

    // High-rated restaurant data
    private List<Restaurant> highRatedRestaurants;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fine = Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false));
                boolean coarse = Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false));
                if (fine || coarse) {
                    enableMyLocationAndLoad();
                } else {
                    Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
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

        // Initialize zoom in/out buttons
        btnZoomIn = view.findViewById(R.id.btn_zoom_in);
        btnZoomOut = view.findViewById(R.id.btn_zoom_out);
        
        // Set button click listeners
        btnZoomIn.setOnClickListener(v -> zoomIn());
        btnZoomOut.setOnClickListener(v -> zoomOut());

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
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);
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
        
        // Show loading prompt
        Toast.makeText(requireContext(), "Loading restaurant data from Firebase...", Toast.LENGTH_SHORT).show();
        
        Log.d(TAG, "Starting to load restaurant data from Firebase...");
        
        db.collection("restaurants")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Firebase connection successful, document count: " + queryDocumentSnapshots.size());
                    
                    if (queryDocumentSnapshots.isEmpty()) {
                        // No data in Firebase
                        Log.d(TAG, "No restaurant data in Firebase");
                        Toast.makeText(requireContext(), "No restaurant data in Firebase, please wait for data upload to complete", Toast.LENGTH_LONG).show();
                    } else {
                        // Data exists in Firebase, load and display
                        List<Restaurant> restaurants = new ArrayList<>();
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            try {
                                Restaurant restaurant = document.toObject(Restaurant.class);
                                restaurant.setId(document.getId());
                                restaurants.add(restaurant);
                                
                                // Add map marker
                                LatLng position = new LatLng(restaurant.getLatitude(), restaurant.getLongitude());
                                MarkerOptions markerOptions = new MarkerOptions()
                                        .position(position)
                                        .title(restaurant.getName())
                                        .snippet(restaurant.getAddress())
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));
                                
                                Marker marker = googleMap.addMarker(markerOptions);
                                if (marker != null) {
                                    marker.setTag(restaurant);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to parse restaurant data: " + document.getId(), e);
                            }
                        }
                        
                        Log.d(TAG, "Successfully loaded " + restaurants.size() + " restaurants");
                        Toast.makeText(requireContext(), "Loaded " + restaurants.size() + " highly-rated restaurants from Firebase", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firebase loading failed", e);
                    Toast.makeText(requireContext(), "Firebase connection failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Show bottom sheet with posts related to the restaurant
     */
    private void showRestaurantPostsBottomSheet(Restaurant restaurant) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_restaurant_posts, null);
        bottomSheet.setContentView(view);

        // Initialize views
        TextView tvRestaurantName = view.findViewById(R.id.tv_restaurant_name);
        TextView tvRestaurantAddress = view.findViewById(R.id.tv_restaurant_address);
        TextView tvPostsCount = view.findViewById(R.id.tv_posts_count);
        RecyclerView rvPosts = view.findViewById(R.id.rv_posts);
        TextView tvNoPosts = view.findViewById(R.id.tv_no_posts);
        androidx.appcompat.widget.AppCompatButton btnNavigate = view.findViewById(R.id.btn_navigate);
        
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

        // Setup RecyclerView
        List<Review> reviews = new ArrayList<>();
        ReviewWidgetAdapter adapter = new ReviewWidgetAdapter(reviews, new ReviewWidgetAdapter.OnReviewClickListener() {
            @Override
            public void onReviewClick(Review review, Restaurant restaurantData) {
                // Open review details dialog
                ReviewDetailsDialog dialog = new ReviewDetailsDialog(requireContext(), review, restaurantData);
                dialog.show();
                bottomSheet.dismiss();
            }
            
            @Override
            public void onUserClick(String userId) {
                // Navigate to user profile
                Intent intent = new Intent(getActivity(), UserProfileActivity.class);
                intent.putExtra("userId", userId);
                startActivity(intent);
            }
        });
        rvPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvPosts.setAdapter(adapter);

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
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading restaurant reviews", e);
                Toast.makeText(requireContext(), "Failed to load reviews", Toast.LENGTH_SHORT).show();
            });
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

    /**
     * Load and display crowd density for a restaurant
     */
    private void loadCrowdDensity(String restaurantId, View indicator, TextView status, 
                                  TextView description, TextView feedbackCount) {
        crowdDensityService.calculateCrowdDensity(restaurantId, new CrowdDensityService.CrowdDensityCallback() {
            @Override
            public void onSuccess(CrowdDensityService.CrowdDensityResult result) {
                // Update UI on main thread
                requireActivity().runOnUiThread(() -> {
                    // Set indicator color
                    int colorResId = result.getColorResId();
                    indicator.setBackgroundColor(ContextCompat.getColor(requireContext(), colorResId));
                    
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

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error loading crowd density", e);
                requireActivity().runOnUiThread(() -> {
                    status.setText("Error loading status");
                    description.setText("Please try again later");
                    feedbackCount.setText("Error");
                });
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
            Toast.makeText(requireContext(), "Please login to submit feedback", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentUserId = mAuth.getCurrentUser().getUid();
        
        crowdDensityService.submitFeedback(restaurantId, currentUserId, crowdingLevel, 
            new CrowdDensityService.FeedbackSubmitCallback() {
                @Override
                public void onSuccess() {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Feedback submitted successfully!", Toast.LENGTH_SHORT).show();
                        // Refresh crowd density display after successful submission
                        loadCrowdDensity(restaurantId, indicator, status, description, feedbackCount);
                    });
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error submitting feedback", e);
                    requireActivity().runOnUiThread(() -> {
                        String errorMessage = e.getMessage();
                        if (errorMessage != null && errorMessage.contains("already submitted")) {
                            Toast.makeText(requireContext(), "Please wait before submitting another feedback", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "Failed to submit feedback: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
    }
    
    /**
     * Navigate to restaurant using Google Maps
     */
    private void navigateToRestaurant(Restaurant restaurant) {
        try {
            // Create URI for Google Maps navigation
            String uri = String.format("google.navigation:q=%f,%f&mode=d", 
                restaurant.getLatitude(), restaurant.getLongitude());
            
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri));
            intent.setPackage("com.google.android.apps.maps");
            
            // Check if Google Maps is installed
            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
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
            Toast.makeText(requireContext(), "Unable to open navigation, please check if Google Maps is installed", Toast.LENGTH_SHORT).show();
        }
    }
    
    // Upload functionality removed, now only load data from Firebase
}
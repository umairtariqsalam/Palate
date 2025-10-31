package com.example.food.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.food.R;
import com.example.food.adapters.CommentsAdapter;
import com.example.food.adapters.ImagePagerAdapter;
import com.example.food.data.Review;
import com.example.food.model.Restaurant;
import com.example.food.services.UserStatsService;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReviewDetailsDialog extends Dialog {

    private static final String TAG = "ReviewDetailsDialog";

    private Review review;
    private Restaurant restaurant;
    private FirebaseFirestore db;

    // UI Components
    private ImageView btnClose;
    private ImageView btnMoreOptions;
    private TextView tvAuthorName;
    private TextView tvRestaurantName;
    private TextView tvRestaurantAddress;
    private TextView tvRestaurantCategory;
    private TextView tvRestaurantLocation;
    private LinearLayout categoryLocationContainer;
    private ViewPager2 imagePager;
    private LinearLayout starContainer;
    private ImageView[] stars;
    private TextView tvRatingValue;
    private TextView tvDialogAccuracyPercent;
    private TextView tvDialogCaption;
    private TextView tvDialogDescription;
    private TextView tvDialogCaptionExpanded;
    private ScrollView captionScrollView;
    private TextView btnExpandCaption;
    private TextView tvVoteCount;
    private ImageView btnAccurateIcon;
    private ImageView btnInaccurateIcon;
    private ImageView btnOpenComments;
    private LinearLayout bottomBar;

    // Adapters
    private ImagePagerAdapter imagePagerAdapter;
    private CommentsAdapter commentsAdapter;

    // Data
    private String currentUserId;
    private Boolean currentUserVote;

    public ReviewDetailsDialog(@NonNull Context context, Review review, Restaurant restaurant) {
        super(context, R.style.FullScreenDialog);
        this.review = review;
        this.restaurant = restaurant;
        this.db = FirebaseFirestore.getInstance();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_review_details);

        // Set status bar to black with white icons for this dialog only
        Window window = getWindow();
        if (window != null) {
            window.setStatusBarColor(Color.BLACK);
            // Set status bar icons to white (default for black background)
            // Remove LIGHT_STATUS_BAR flag to ensure white icons on black background
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // Ensure white icons
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }

        initViews();
        populateData();
        setupClickListeners();
    }

    private void initViews() {
        // Header
        btnClose = findViewById(R.id.btnClose);
        tvAuthorName = findViewById(R.id.tvAuthorName);
        tvRestaurantName = findViewById(R.id.tvRestaurantName);
        tvRestaurantAddress = findViewById(R.id.tvRestaurantAddress);
        tvRestaurantCategory = findViewById(R.id.tvRestaurantCategory);
        tvRestaurantLocation = findViewById(R.id.tvRestaurantLocation);
        categoryLocationContainer = findViewById(R.id.categoryLocationContainer);
        btnMoreOptions = findViewById(R.id.btnMoreOptions);

        // Image gallery
        imagePager = findViewById(R.id.imagePager);
        // imageIndicator removed from new layout

        // Rating and accuracy
        starContainer = findViewById(R.id.starContainer);
        tvRatingValue = findViewById(R.id.tvRatingValue);
        tvDialogAccuracyPercent = findViewById(R.id.tvDialogAccuracyPercent);
        tvDialogCaption = findViewById(R.id.tvDialogCaption);
        tvDialogDescription = findViewById(R.id.tvDialogDescription);
        tvDialogCaptionExpanded = findViewById(R.id.tvDialogCaptionExpanded);
        captionScrollView = findViewById(R.id.captionScrollView);
        btnExpandCaption = findViewById(R.id.btnExpandCaption);

        // Initialize star views
        stars = new ImageView[5];
        stars[0] = findViewById(R.id.star1);
        stars[1] = findViewById(R.id.star2);
        stars[2] = findViewById(R.id.star3);
        stars[3] = findViewById(R.id.star4);
        stars[4] = findViewById(R.id.star5);

        // Voting
        // tvVoteCount removed from new layout
        btnAccurateIcon = findViewById(R.id.btnAccurateIcon);
        btnInaccurateIcon = findViewById(R.id.btnInaccurateIcon);

        btnOpenComments = findViewById(R.id.btnOpenComments);
        
        // Bottom bar
        bottomBar = findViewById(R.id.bottomBar);

        // Get current user
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = user != null ? user.getUid() : null;

        // Initialize adapters
        setupAdapters();
    }


    private void setupAdapters() {
        // Image pager adapter
        imagePagerAdapter = new ImagePagerAdapter(getContext());
        imagePager.setAdapter(imagePagerAdapter);
    }

    private void populateData() {
        // Author header (fallbacks for now)
        if (tvAuthorName != null) {
            tvAuthorName.setText(getContext().getString(R.string.loading));
        }

        // Restaurant name - fetch from restaurant ID if not provided
        if (tvRestaurantName != null) {
            if (restaurant != null) {
                tvRestaurantName.setText(restaurant.getName());
                // Set address from restaurant object if available
                if (tvRestaurantAddress != null) {
                    tvRestaurantAddress.setText(restaurant.getAddress() != null ? restaurant.getAddress() : "Address not available");
                }
                // Set category and location from restaurant object if available
                if (tvRestaurantCategory != null) {
                    tvRestaurantCategory.setText(restaurant.getCategory() != null ? restaurant.getCategory() : "Restaurant");
                }
                if (tvRestaurantLocation != null) {
                    tvRestaurantLocation.setText(restaurant.getRegion() != null ? restaurant.getRegion() : "Location");
                }
            } else if (review.getRestaurantId() != null) {
                fetchRestaurantName(review.getRestaurantId());
            } else {
                tvRestaurantName.setText(getContext().getString(R.string.restaurant_placeholder));
                if (tvRestaurantAddress != null) {
                    tvRestaurantAddress.setText("Address not available");
                }
                if (tvRestaurantCategory != null) {
                    tvRestaurantCategory.setText("Restaurant");
                }
                if (tvRestaurantLocation != null) {
                    tvRestaurantLocation.setText("Location");
                }
            }
        }

        // Date removed - replaced with directions and more options buttons

        // Rating - show stars and value
        setStarRating(review.getRating());
        tvRatingValue.setText(String.format(Locale.getDefault(), "%.1f", review.getRating()));

        // Accuracy with color coding and dynamic calculation
        updateAccuracyDisplay();

        // Caption with expand/collapse functionality
        setupCaptionDisplay();

        // Images - setup pager
        setupImageGallery();

        // Voting
        updateVoteDisplay();
        updateVoteButtons();

        // Fetch and set actual username
        fetchUsername();
    }

    private void setupImageGallery() {
        if (review.getImageUrls() != null && !review.getImageUrls().isEmpty()) {
            imagePagerAdapter.setImages(review.getImageUrls());
        } else {
            // Show placeholder
            List<String> placeholder = new ArrayList<>();
            placeholder.add("placeholder");
            imagePagerAdapter.setImages(placeholder);
        }
    }

    private void setupComments() {
       
    }

    private void updateAccuracyDisplay() {
        // Calculate accuracy dynamically from votes
        double accuracy = calculateAccuracyFromVotes();
        tvDialogAccuracyPercent.setText(String.format(Locale.getDefault(), "%.0f%%", accuracy));

        // Update the accuracy percent in the review object for consistency
        review.setAccuracyPercent(accuracy);
    }

    private double calculateAccuracyFromVotes() {
        if (review.getVotes() == null || review.getVotes().isEmpty()) {
            return 0.0;
        }

        int accurateVotes = 0;
        int totalVotes = review.getVotes().size();

        for (Map<String, Object> voteData : review.getVotes().values()) {
            Boolean vote = (Boolean) voteData.get("accurate");
            if (vote != null && vote) {
                accurateVotes++;
            }
        }

        return totalVotes > 0 ? (accurateVotes * 100.0) / totalVotes : 0.0;
    }

    private void updateVoteDisplay() {
        if (tvVoteCount != null) {
            if (review.getVotes() != null) {
                int voteCount = review.getVotes().size();
                String text = voteCount == 1 ?
                    "1 person found this accurate" :
                    voteCount + " people found this accurate";
                tvVoteCount.setText(text);
            } else {
                tvVoteCount.setText("0 people found this accurate");
            }
        }
    }

    private void fetchUsername() {
        if (review == null || review.getUserId() == null) {
            tvAuthorName.setText(getContext().getString(R.string.username_placeholder));
            return;
        }

        db.collection("users")
            .document(review.getUserId())
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                 
                    String name = documentSnapshot.getString("name");
                    if (name != null && !name.trim().isEmpty()) {
                        tvAuthorName.setText(name);
                    } else {
                   
                        String username = documentSnapshot.getString("username");
                        if (username != null && !username.trim().isEmpty()) {
                            tvAuthorName.setText(username);
                        } else {
                            tvAuthorName.setText(getContext().getString(R.string.username_placeholder));
                        }
                    }
                } else {
                    tvAuthorName.setText(getContext().getString(R.string.username_placeholder));
                }
            })
            .addOnFailureListener(e -> {
                tvAuthorName.setText(getContext().getString(R.string.username_placeholder));
            });
    }

    private void updateVoteButtons() {
        if (currentUserId == null) {
            // User not logged in
            btnAccurateIcon.setEnabled(false);
            btnInaccurateIcon.setEnabled(false);
            return;
        }

        // Check current user's vote
        if (review.getVotes() != null && review.getVotes().containsKey(currentUserId)) {
            Map<String, Object> voteData = review.getVotes().get(currentUserId);
            currentUserVote = (Boolean) voteData.get("accurate");
            if (currentUserVote != null && currentUserVote) {
                btnAccurateIcon.setSelected(true);
                btnInaccurateIcon.setSelected(false);
                // Update icon colors
                btnAccurateIcon.setColorFilter(getContext().getColor(R.color.modern_success));
                btnInaccurateIcon.setColorFilter(getContext().getColor(R.color.white));
            } else {
                btnAccurateIcon.setSelected(false);
                btnInaccurateIcon.setSelected(true);
                // Update icon colors
                btnAccurateIcon.setColorFilter(getContext().getColor(R.color.white));
                btnInaccurateIcon.setColorFilter(getContext().getColor(R.color.modern_error));
            }
        } else {
            currentUserVote = null;
            btnAccurateIcon.setSelected(false);
            btnInaccurateIcon.setSelected(false);
            // Reset icon colors
            btnAccurateIcon.setColorFilter(getContext().getColor(R.color.white));
            btnInaccurateIcon.setColorFilter(getContext().getColor(R.color.white));
        }

        btnAccurateIcon.setEnabled(true);
        btnInaccurateIcon.setEnabled(true);
    }

    private void setupClickListeners() {
        // Close button
        btnClose.setOnClickListener(v -> dismiss());

        // Vote buttons
        btnAccurateIcon.setOnClickListener(v -> {
            // Start animation
            v.startAnimation(android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.button_click_animation));
            vote(true);
        });
        btnInaccurateIcon.setOnClickListener(v -> {
            // Start animation
            v.startAnimation(android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.button_click_animation));
            vote(false);
        });

        // Comments bottom sheet
        if (btnOpenComments != null) {
            btnOpenComments.setOnClickListener(v -> openCommentsBottomSheet());
        }

        // More options button
        btnMoreOptions.setOnClickListener(v -> showMoreOptions());

        // Caption expand/collapse
        if (btnExpandCaption != null) {
            btnExpandCaption.setOnClickListener(v -> toggleCaptionExpansion());
        }
    }

    private void openCommentsBottomSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(getContext());
        View view = View.inflate(getContext(), R.layout.bottom_sheet_comments, null);

        RecyclerView commentsList = view.findViewById(R.id.rvComments);
        TextView empty = view.findViewById(R.id.tvNoComments);
        EditText etCommentInput = view.findViewById(R.id.etCommentInput);
        ImageView btnSendComment = view.findViewById(R.id.btnSendComment);

        CommentsAdapter adapter = new CommentsAdapter(review.getComments() != null ? review.getComments() : new ArrayList<>());
        commentsList.setLayoutManager(new LinearLayoutManager(getContext()));
        commentsList.setAdapter(adapter);

        if (review.getComments() != null && !review.getComments().isEmpty()) {
            adapter.setComments(review.getComments());
            commentsList.setVisibility(View.VISIBLE);
            empty.setVisibility(View.GONE);
        } else {
            commentsList.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
        }

        // Set up comment input functionality
        btnSendComment.setOnClickListener(v -> {
            String commentText = etCommentInput.getText().toString().trim();
            if (commentText.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a comment", Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentUserId == null) {
                Toast.makeText(getContext(), "Please log in to comment", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validate review ID
            if (review.getId() == null || review.getId().trim().isEmpty()) {
                Toast.makeText(getContext(), "Invalid review ID", Toast.LENGTH_SHORT).show();
                return;
            }

            // Disable send button
            btnSendComment.setEnabled(false);

            // Fetch username from Firestore users collection
            db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String username;
                    if (documentSnapshot.exists() && documentSnapshot.getString("name") != null) {
                        username = documentSnapshot.getString("name");
                    } else {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        username = user != null && user.getEmail() != null ?
                            user.getEmail().split("@")[0] : "Anonymous";
                    }

                    // Create comment data map
                    Map<String, Object> commentData = new HashMap<>();
                    commentData.put("userId", currentUserId);
                    commentData.put("userName", username);
                    commentData.put("text", commentText);
                    commentData.put("createdAt", com.google.firebase.Timestamp.now());

                    // Add comment to reviews document's comments array
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("comments", com.google.firebase.firestore.FieldValue.arrayUnion(commentData));

                    db.collection("reviews").document(review.getId())
                        .update(updates)
                        .addOnSuccessListener(aVoid -> {
                            // Clear input
                            etCommentInput.setText("");
                            btnSendComment.setEnabled(true);
                            
                            // Immediately add the new comment to the current review object
                            addCommentToCurrentReview(commentData);
                            
                            // Refresh the comments in the bottom sheet
                            refreshCommentsInBottomSheet(adapter, commentsList, empty);
                            
                            Toast.makeText(getContext(), "Comment added", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error adding comment", e);
                            btnSendComment.setEnabled(true);
                            Toast.makeText(getContext(), "Failed to add comment: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching username", e);
                    btnSendComment.setEnabled(true);
                    Toast.makeText(getContext(), "Failed to fetch user info", Toast.LENGTH_SHORT).show();
                });
        });

        sheet.setContentView(view);
        sheet.show();
    }
    
    public void openCommentsSection() {
        // trigger the comments bottom sheet to open
        openCommentsBottomSheet();
    }

    private void addCommentToCurrentReview(Map<String, Object> commentData) {
        // Convert the comment data to Comment object
        com.example.food.data.Comment newComment = new com.example.food.data.Comment();
        newComment.setUserId((String) commentData.get("userId"));
        newComment.setUserName((String) commentData.get("userName"));
        newComment.setText((String) commentData.get("text"));
        com.google.firebase.Timestamp timestamp = (com.google.firebase.Timestamp) commentData.get("createdAt");
        newComment.setCreatedAt(timestamp != null ? timestamp.toDate() : null);
        
        // Add to current review's comments list
        if (review.getComments() == null) {
            review.setComments(new ArrayList<>());
        }
        review.getComments().add(newComment);
        
        // Sort comments by timestamp (newest first)
        review.getComments().sort((c1, c2) -> {
            if (c1.getCreatedAt() == null) return 1;
            if (c2.getCreatedAt() == null) return -1;
            return c2.getCreatedAt().compareTo(c1.getCreatedAt());
        });
    }

    private void refreshCommentsInBottomSheet(CommentsAdapter adapter, RecyclerView commentsList, TextView empty) {
        // First try to use comments from current review object (for immediate updates)
        if (review.getComments() != null && !review.getComments().isEmpty()) {
            adapter.setComments(review.getComments());
            
            if (review.getComments().isEmpty()) {
                commentsList.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
            } else {
                commentsList.setVisibility(View.VISIBLE);
                empty.setVisibility(View.GONE);
            }
            return;
        }
        
        // If no comments in current review object, load from Firestore
        db.collection("reviews").document(review.getId())
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    List<Map<String, Object>> commentsData = 
                        (List<Map<String, Object>>) documentSnapshot.get("comments");
                    
                    List<com.example.food.data.Comment> comments = new ArrayList<>();
                    if (commentsData != null) {
                        for (Map<String, Object> commentMap : commentsData) {
                            com.example.food.data.Comment comment = new com.example.food.data.Comment();
                            comment.setUserId((String) commentMap.get("userId"));
                            comment.setUserName((String) commentMap.get("userName"));
                            comment.setText((String) commentMap.get("text"));
                            com.google.firebase.Timestamp timestamp = (com.google.firebase.Timestamp) commentMap.get("createdAt");
                            comment.setCreatedAt(timestamp != null ? timestamp.toDate() : null);
                            comments.add(comment);
                        }
                    }

                    // Sort comments by timestamp (newest first)
                    comments.sort((c1, c2) -> {
                        if (c1.getCreatedAt() == null) return 1;
                        if (c2.getCreatedAt() == null) return -1;
                        return c2.getCreatedAt().compareTo(c1.getCreatedAt());
                    });

                    // Update the current review object with loaded comments
                    review.setComments(comments);
                    
                    adapter.setComments(comments);
                    
                    if (comments.isEmpty()) {
                        commentsList.setVisibility(View.GONE);
                        empty.setVisibility(View.VISIBLE);
                    } else {
                        commentsList.setVisibility(View.VISIBLE);
                        empty.setVisibility(View.GONE);
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error refreshing comments", e);
            });
    }

    private void vote(boolean accurate) {
        if (currentUserId == null) {
            Toast.makeText(getContext(), "Please log in to vote", Toast.LENGTH_SHORT).show();
            return;
        }

        // Update local state first for immediate UI feedback
        if (review.getVotes() == null) {
            review.setVotes(new HashMap<>());
        }

        boolean wasPreviouslyVoted = review.getVotes().containsKey(currentUserId);
        final Boolean previousVote;
        if (wasPreviouslyVoted && review.getVotes().get(currentUserId) != null) {
            previousVote = (Boolean) review.getVotes().get(currentUserId).get("accurate");
        } else {
            previousVote = null;
        }

        // If clicking the same button, remove vote
        if (wasPreviouslyVoted && ((previousVote != null && previousVote == accurate))) {
            review.getVotes().remove(currentUserId);
        } else {
            Map<String, Object> voteData = new HashMap<>();
            voteData.put("accurate", accurate);
            voteData.put("timestamp", new java.util.Date());
            review.getVotes().put(currentUserId, voteData);
        }

            // Update UI immediately
            updateVoteDisplay();
            updateVoteButtons();
            updateAccuracyDisplay(); // Recalculate accuracy after vote

            // Update in Firebase
        Map<String, Object> updates = new HashMap<>();
        updates.put("votes", review.getVotes());
        updates.put("accuracyPercent", review.getAccuracyPercent());

        db.collection("reviews").document(review.getId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Vote and accuracy updated successfully");
                    // Update the review author's scores when their review gets voted on
                    UserStatsService.updateUserScoresOnVoteChange(review.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating vote", e);
                    Toast.makeText(getContext(), "Failed to update vote", Toast.LENGTH_SHORT).show();

                    // Revert local changes on error
                    if (wasPreviouslyVoted) {
                        Map<String, Object> voteData = new HashMap<>();
                        voteData.put("accurate", previousVote);
                        voteData.put("timestamp", new java.util.Date());
                        review.getVotes().put(currentUserId, voteData);
                    } else {
                        review.getVotes().remove(currentUserId);
                    }
                    updateVoteDisplay();
                    updateVoteButtons();
                    updateAccuracyDisplay();
                });
    }

    private void setStarRating(double rating) {
        for (int i = 0; i < 5; i++) {
            // Calculate fill amount for this star: (rating - i), clamped between 0 and 1
            double fillAmount = Math.max(0.0, Math.min(1.0, rating - i));
            
            // Get the drawable and check if it's a LayerDrawable
            android.graphics.drawable.Drawable drawable = stars[i].getDrawable();
            if (drawable instanceof LayerDrawable) {
                // Use LayerDrawable with ClipDrawable for precise fills
                LayerDrawable layerDrawable = (LayerDrawable) drawable;
                int level = (int) (fillAmount * 10000);
                ClipDrawable clipDrawable = (ClipDrawable) layerDrawable.findDrawableByLayerId(android.R.id.progress);
                if (clipDrawable != null) {
                    clipDrawable.setLevel(level);
                }
            } else {
                // Fallback: use alpha-based method
                if (fillAmount >= 1.0) {
                    stars[i].setImageResource(R.drawable.ic_star_filled);
                    stars[i].setColorFilter(null);
                    stars[i].setAlpha(1.0f);
                } else if (fillAmount > 0.0) {
                    stars[i].setImageResource(R.drawable.ic_star_filled);
                    stars[i].setColorFilter(null);
                    stars[i].setAlpha((float) fillAmount);
                } else {
                    stars[i].setImageResource(R.drawable.ic_star_empty);
                    stars[i].setColorFilter(null);
                    stars[i].setAlpha(1.0f);
                }
            }
        }
    }

    private void openDirections() {
        if (restaurant != null && restaurant.getLatitude() != 0 && restaurant.getLongitude() != 0) {
            String uri = "geo:" + restaurant.getLatitude() + "," + restaurant.getLongitude() + "?q=" + 
                        Uri.encode(restaurant.getName());
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage("com.google.android.apps.maps");
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                getContext().startActivity(intent);
            } else {
                // Fallback to web maps
                String webUri = "https://www.google.com/maps/search/?api=1&query=" + 
                               Uri.encode(restaurant.getName());
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUri));
                getContext().startActivity(webIntent);
            }
        } else {
            Toast.makeText(getContext(), "Location not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void showMoreOptions() {
        PopupMenu popup = new PopupMenu(getContext(), btnMoreOptions);
        popup.getMenuInflater().inflate(R.menu.review_more_options, popup.getMenu());
        
        // Hide delete option if current user is not the author of the review
        if (currentUserId == null || !currentUserId.equals(review.getUserId())) {
            popup.getMenu().findItem(R.id.action_delete_post).setVisible(false);
        }
        
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_directions) {
                openDirections();
                return true;
            } else if (itemId == R.id.action_search_restaurant) {
                // Open MapFragment and have it open this restaurant sheet
                if (restaurant != null && restaurant.getId() != null) {
                    android.content.Intent intent = new android.content.Intent(getContext(), com.example.food.MainActivity.class);
                    intent.putExtra("open_restaurant_id", restaurant.getId());
                    getContext().startActivity(intent);
                } else if (review != null && review.getRestaurantId() != null) {
                    android.content.Intent intent = new android.content.Intent(getContext(), com.example.food.MainActivity.class);
                    intent.putExtra("open_restaurant_id", review.getRestaurantId());
                    getContext().startActivity(intent);
                } else {
                    Toast.makeText(getContext(), "No restaurant data found", Toast.LENGTH_SHORT).show();
                }
                return true;
            } else if (itemId == R.id.action_delete_post) {
                // Double check: only allow deletion if user is the author
                if (currentUserId != null && currentUserId.equals(review.getUserId())) {
                    showDeleteConfirmation();
                } else {
                    Toast.makeText(getContext(), "You can only delete your own posts", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });
        
        popup.show();
    }

    private void fetchRestaurantName(String restaurantId) {
        if (restaurantId == null || restaurantId.trim().isEmpty()) {
            tvRestaurantName.setText(getContext().getString(R.string.restaurant_placeholder));
            if (tvRestaurantAddress != null) {
                tvRestaurantAddress.setText("Address not available");
            }
            tvRestaurantCategory.setText("Restaurant");
            tvRestaurantLocation.setText("Location");
            return;
        }

        db.collection("restaurants")
            .document(restaurantId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // Set restaurant name
                    String restaurantName = documentSnapshot.getString("name");
                    if (restaurantName != null && !restaurantName.trim().isEmpty()) {
                        tvRestaurantName.setText(restaurantName);
                    } else {
                        tvRestaurantName.setText(getContext().getString(R.string.restaurant_placeholder));
                    }

                    // Set restaurant address
                    String address = documentSnapshot.getString("address");
                    if (tvRestaurantAddress != null) {
                        tvRestaurantAddress.setText(address != null && !address.trim().isEmpty() ? address : "Address not available");
                    }

                    // Set restaurant category
                    String category = documentSnapshot.getString("category");
                    if (tvRestaurantCategory != null) {
                        tvRestaurantCategory.setText(category != null && !category.trim().isEmpty() ? category : "Restaurant");
                    }

                    // Set restaurant location/region
                    String region = documentSnapshot.getString("region");
                    if (tvRestaurantLocation != null) {
                        tvRestaurantLocation.setText(region != null && !region.trim().isEmpty() ? region : "Location");
                    }
                } else {
                    tvRestaurantName.setText(getContext().getString(R.string.restaurant_placeholder));
                    if (tvRestaurantAddress != null) {
                        tvRestaurantAddress.setText("Address not available");
                    }
                    tvRestaurantCategory.setText("Restaurant");
                    tvRestaurantLocation.setText("Location");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error fetching restaurant name", e);
                tvRestaurantName.setText(getContext().getString(R.string.restaurant_placeholder));
                tvRestaurantCategory.setText("Restaurant");
                tvRestaurantLocation.setText("Location");
            });
    }

    private void setupCaptionDisplay() {
        String caption = review.getCaption();
        String description = review.getDescription();

        // Show caption (short text) first
        if (caption == null || caption.trim().isEmpty()) {
            tvDialogCaption.setText(getContext().getString(R.string.description_placeholder));
        } else {
            tvDialogCaption.setText(caption.trim());
        }

        // Hide description initially - will show when expanded
        tvDialogDescription.setVisibility(View.GONE);

        // Prepare expanded content with both caption and description
        StringBuilder expandedContent = new StringBuilder();
        if (caption != null && !caption.trim().isEmpty()) {
            expandedContent.append(caption.trim());
        }
        if (description != null && !description.trim().isEmpty()) {
            if (expandedContent.length() > 0) {
                expandedContent.append("\n\n");
            }
            expandedContent.append(description.trim());
        }
        
        tvDialogCaptionExpanded.setText(expandedContent.toString());

        // Show expand button if there's description content or caption is long
        boolean hasDescription = description != null && !description.trim().isEmpty();
        boolean captionIsLong = caption != null && caption.trim().length() > 50;
        
        if (hasDescription || captionIsLong) {
            btnExpandCaption.setVisibility(View.VISIBLE);
            btnExpandCaption.setText(getContext().getString(R.string.read_more));
            tvDialogCaption.setMaxLines(1);
            tvDialogCaption.setEllipsize(android.text.TextUtils.TruncateAt.END);
            // Hide the FrameLayout container (which contains the ScrollView)
            View captionContainer = (View) findViewById(R.id.captionScrollView).getParent();
            if (captionContainer != null) {
                captionContainer.setVisibility(View.GONE);
            }
            // No background when collapsed
            bottomBar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        } else {
            btnExpandCaption.setVisibility(View.GONE);
            tvDialogCaption.setMaxLines(Integer.MAX_VALUE);
            tvDialogCaption.setEllipsize(null);
            // Hide the FrameLayout container (which contains the ScrollView)
            View captionContainer = (View) findViewById(R.id.captionScrollView).getParent();
            if (captionContainer != null) {
                captionContainer.setVisibility(View.GONE);
            }
            // No background for short captions
            bottomBar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
    }

    private void toggleCaptionExpansion() {
        if (btnExpandCaption.getText().toString().equals(getContext().getString(R.string.read_more))) {
            // Expand - show scrollable text
            tvDialogCaption.setVisibility(View.GONE);
            // Show the FrameLayout container (which contains the ScrollView)
            View captionContainer = (View) findViewById(R.id.captionScrollView).getParent();
            if (captionContainer != null) {
                captionContainer.setVisibility(View.VISIBLE);
            }
            btnExpandCaption.setText(getContext().getString(R.string.read_less));
            // Add background when expanded
            bottomBar.setBackgroundResource(R.drawable.dialog_background);
            // Show category and location when expanded
            if (categoryLocationContainer != null) {
                categoryLocationContainer.setVisibility(View.VISIBLE);
            }
            // Hide action icons when footer is expanded
            View actionIcons = findViewById(R.id.actionIcons);
            if (actionIcons != null) {
                actionIcons.setVisibility(View.GONE);
            }
        } else {
            // Collapse - back to 1 line
            tvDialogCaption.setVisibility(View.VISIBLE);
            // Hide the FrameLayout container (which contains the ScrollView)
            View captionContainer = (View) findViewById(R.id.captionScrollView).getParent();
            if (captionContainer != null) {
                captionContainer.setVisibility(View.GONE);
            }
            btnExpandCaption.setText(getContext().getString(R.string.read_more));
            // Remove background when collapsed
            bottomBar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            // Hide category and location when collapsed
            if (categoryLocationContainer != null) {
                categoryLocationContainer.setVisibility(View.GONE);
            }
            // Show action icons when footer is collapsed
            View actionIcons = findViewById(R.id.actionIcons);
            if (actionIcons != null) {
                actionIcons.setVisibility(View.VISIBLE);
            }
        }
        
        // Request layout update to ensure proper positioning
        tvDialogCaption.requestLayout();
        captionScrollView.requestLayout();
        btnExpandCaption.requestLayout();
    }

    private void showDeleteConfirmation() {
        // Check if current user is the author of the review
        if (currentUserId == null || !currentUserId.equals(review.getUserId())) {
            Toast.makeText(getContext(), "You can only delete your own posts", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show confirmation dialog using AlertDialog
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
            .setTitle("Delete Review")
            .setMessage("Are you sure you want to delete this review? This action cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> deleteReview())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void deleteReview() {
        if (review.getId() == null || review.getId().trim().isEmpty()) {
            Toast.makeText(getContext(), "Failed to delete review", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        Toast.makeText(getContext(), "Deleting review...", Toast.LENGTH_SHORT).show();

        // Delete from Firestore
        db.collection("reviews")
            .document(review.getId())
            .delete()
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Review deleted successfully");
                Toast.makeText(getContext(), "Review deleted successfully", Toast.LENGTH_SHORT).show();
                
                // Close the dialog
                dismiss();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error deleting review", e);
                Toast.makeText(getContext(), "Failed to delete review: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
}
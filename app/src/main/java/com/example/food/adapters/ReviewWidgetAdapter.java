package com.example.food.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.food.R;
import com.example.food.data.Review;
import com.example.food.model.Restaurant;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReviewWidgetAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_REVIEW = 0;
    private static final int VIEW_TYPE_SKELETON = 1;

    private List<Review> reviews;
    private Map<String, Restaurant> restaurantMap;
    private OnReviewClickListener listener;
    private boolean isLoading = false;
    private boolean showUserInfo = true;


    public interface OnReviewClickListener {
        void onReviewClick(Review review, Restaurant restaurant);
        void onUserClick(String userId);
    }

    public ReviewWidgetAdapter(List<Review> reviews, OnReviewClickListener listener) {
        this.reviews = reviews;
        this.listener = listener;
        this.restaurantMap = new HashMap<>();
    }

    public ReviewWidgetAdapter(List<Review> reviews, OnReviewClickListener listener, boolean showUserInfo) {
        this.reviews = reviews;
        this.listener = listener;
        this.restaurantMap = new HashMap<>();
        this.showUserInfo = showUserInfo;
    }

    @Override
    public int getItemViewType(int position) {
        if (isLoading && position < 8) { // Show 8 skeleton cards so it looks better
            return VIEW_TYPE_SKELETON;
        }
        return VIEW_TYPE_REVIEW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SKELETON) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_review_card_skeleton, parent, false);
            return new SkeletonViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_review_card, parent, false);
            return new ReviewViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SkeletonViewHolder) {
           
            return;
        } else if (holder instanceof ReviewViewHolder) {
            ReviewViewHolder reviewHolder = (ReviewViewHolder) holder;
            int reviewPosition = isLoading ? position - 8 : position;
            if (reviewPosition >= 0 && reviewPosition < reviews.size()) {
                Review review = reviews.get(reviewPosition);
                reviewHolder.bind(review);
            }
        }
    }

    @Override
    public int getItemCount() {
        return isLoading ? (reviews != null ? reviews.size() + 8 : 8) : (reviews != null ? reviews.size() : 0);
    }

    public void setReviews(List<Review> reviews) {
        this.reviews = reviews;
        notifyDataSetChanged();
    }

    public void setLoading(boolean loading) {
        this.isLoading = loading;
        notifyDataSetChanged();
    }

    public void setRestaurantMap(Map<String, Restaurant> restaurantMap) {
        this.restaurantMap = restaurantMap != null ? restaurantMap : new HashMap<>();
        notifyDataSetChanged();
    }

    public void setShowUserInfo(boolean showUserInfo) {
        this.showUserInfo = showUserInfo;
        notifyDataSetChanged();
    }

    class ReviewViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivRestaurantImage;
        private TextView tvCaption;
        private TextView tvRating;
        private TextView tvAccuracy;
        private ImageView ivUserAvatar;
        private TextView tvUserName;
        private View userInfoButton;

        public ReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            ivRestaurantImage = itemView.findViewById(R.id.ivRestaurantImage);
            tvCaption = itemView.findViewById(R.id.tvCaption);
            tvRating = itemView.findViewById(R.id.tvRating);
            tvAccuracy = itemView.findViewById(R.id.tvAccuracy);
            ivUserAvatar = itemView.findViewById(R.id.ivUserAvatar);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            userInfoButton = itemView.findViewById(R.id.userInfoButton);

            // Set up user info button click listener
            if (userInfoButton != null) {
                userInfoButton.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        int reviewPosition = isLoading ? position - 8 : position;
                        if (reviewPosition >= 0 && reviewPosition < reviews.size()) {
                            Review review = reviews.get(reviewPosition);
                            listener.onUserClick(review.getUserId());
                        }
                    }
                });
            }

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    int reviewPosition = isLoading ? position - 8 : position;
                    if (reviewPosition >= 0 && reviewPosition < reviews.size()) {
                        Review review = reviews.get(reviewPosition);
                        Restaurant restaurant = restaurantMap.get(review.getRestaurantId());
                        if (restaurant == null) {
                           
                            restaurant = new Restaurant(review.getRestaurantId(), 
                                review.getRestaurantName() != null ? review.getRestaurantName() : "Unknown Restaurant", 
                                "", 0.0, 0.0, "Restaurant", "Melbourne");
                        }
                        listener.onReviewClick(review, restaurant);
                    }
                }
            });
        }

        public void bind(Review review) {
            if (tvCaption != null && review.getCaption() != null) {
                tvCaption.setText(review.getCaption());
            }
            if (tvRating != null) {
                tvRating.setText(String.format(Locale.getDefault(), "%.1f", review.getRating()));
            }
            if (tvAccuracy != null) {
                tvAccuracy.setText(String.format(Locale.getDefault(), "%.0f%%", review.getAccuracyPercent()));
            }
            
            // Bind user information
            if (showUserInfo) {
                if (tvUserName != null) {
                    String userName = review.getUserName();
                    if (userName != null && !userName.trim().isEmpty()) {
                        tvUserName.setText(userName);
                    } else {
                        tvUserName.setText(itemView.getContext().getString(R.string.user_name_placeholder));
                    }
                }
                
                if (ivUserAvatar != null) {
                    String avatarUrl = review.getUserAvatarUrl();
                    if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                        Glide.with(itemView.getContext())
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_person)
                                .error(R.drawable.ic_person)
                                .circleCrop()
                                .into(ivUserAvatar);
                    } else {
                        ivUserAvatar.setImageResource(R.drawable.ic_person);
                    }
                }
                
                if (userInfoButton != null) {
                    userInfoButton.setVisibility(View.VISIBLE);
                }
            } else {
                if (userInfoButton != null) {
                    userInfoButton.setVisibility(View.GONE);
                }
            }
            if (ivRestaurantImage != null) {
                // Clear any previous state
                ivRestaurantImage.clearColorFilter();
                ivRestaurantImage.setPadding(0, 0, 0, 0);
                
                if (review.getImageUrls() != null && !review.getImageUrls().isEmpty()) {
                    String imageUrl = review.getImageUrls().get(0);

                    // Set ImageView height and scale type based on Firestore field BEFORE loading
                    if (review.getFirstImageType() != null) {
                        int heightPx;
                        if ("PORTRAIT".equals(review.getFirstImageType())) {
                            // Portrait: taller height, centerCrop
                            heightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300, itemView.getContext().getResources().getDisplayMetrics());
                            ivRestaurantImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        } else if ("SQUARE".equals(review.getFirstImageType())) {
                            // Square: medium height, centerCrop
                            heightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, itemView.getContext().getResources().getDisplayMetrics());
                            ivRestaurantImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        } else if ("HORIZONTAL".equals(review.getFirstImageType())) {
                            // Horizontal: shorter height, centerCrop
                            heightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 150, itemView.getContext().getResources().getDisplayMetrics());
                            ivRestaurantImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        } else {
                            heightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, itemView.getContext().getResources().getDisplayMetrics());
                            ivRestaurantImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        }
                        ivRestaurantImage.getLayoutParams().height = heightPx;
                        ivRestaurantImage.requestLayout();
                    } else {
                        // Default fallback
                        int defaultHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, itemView.getContext().getResources().getDisplayMetrics());
                        ivRestaurantImage.getLayoutParams().height = defaultHeightPx;
                        ivRestaurantImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        ivRestaurantImage.requestLayout();
                    }

                    // Load the image with predetermined size
                    Glide.with(itemView.getContext())
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_restaurant)
                            .error(R.drawable.ic_restaurant)
                            .into(ivRestaurantImage);
                } else {
                    ivRestaurantImage.setImageResource(R.drawable.ic_restaurant);
                    int defaultHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, itemView.getContext().getResources().getDisplayMetrics());
                    ivRestaurantImage.getLayoutParams().height = defaultHeightPx;
                    ivRestaurantImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    ivRestaurantImage.requestLayout();
                }
            }
        }
    }

    class SkeletonViewHolder extends RecyclerView.ViewHolder {
        public SkeletonViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}

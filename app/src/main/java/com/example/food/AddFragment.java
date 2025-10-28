package com.example.food;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import androidx.appcompat.widget.AppCompatButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.example.food.data.Review;
import com.example.food.model.Restaurant;
import com.example.food.service.ReviewService;
import com.example.food.services.UserStatsService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AddFragment extends Fragment {
    private static final String TAG = "AddFragment";
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int TAKE_PHOTO_REQUEST = 2;
    private static final int MAX_IMAGES = 5;
    
    private EditText etCaption, etDescription, etRatingInput;
    private AutoCompleteTextView etRestaurantSearch;
    private LinearLayout btnTakePhoto, btnSelectImages;
    private AppCompatButton btnSubmit;
    private TextView tvPhotoCount;
    private ImageView ivPreview1, ivPreview2, ivPreview3, ivPreview4, ivPreview5;
    private ImageView btnDelete1, btnDelete2, btnDelete3, btnDelete4, btnDelete5;
    private ImageView star1, star2, star3, star4, star5;
    
    private List<Restaurant> restaurants;
    private Restaurant selectedRestaurant;
    private List<Uri> selectedImageUris;
    private List<String> uploadedImageUrls;
    private float selectedRating = 0.0f;
    private Uri cameraImageUri;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private ReviewService reviewService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add, container, false);
        
        initViews(view);
        setupListeners();
        initializeFirebase();
        loadRestaurants();
        
        return view;
    }
    
    private void initViews(View view) {
        etCaption = view.findViewById(R.id.et_caption);
        etDescription = view.findViewById(R.id.et_description);
        etRatingInput = view.findViewById(R.id.et_rating_input);
        etRestaurantSearch = view.findViewById(R.id.et_restaurant_search);
        tvPhotoCount = view.findViewById(R.id.tv_photo_count);
        btnTakePhoto = view.findViewById(R.id.btn_take_photo);
        btnSelectImages = view.findViewById(R.id.btn_select_images);
        btnSubmit = view.findViewById(R.id.btn_submit);
        
        // image previews
        ivPreview1 = view.findViewById(R.id.iv_preview_1);
        ivPreview2 = view.findViewById(R.id.iv_preview_2);
        ivPreview3 = view.findViewById(R.id.iv_preview_3);
        ivPreview4 = view.findViewById(R.id.iv_preview_4);
        ivPreview5 = view.findViewById(R.id.iv_preview_5);
        
        // delete buttons
        btnDelete1 = view.findViewById(R.id.btn_delete_1);
        btnDelete2 = view.findViewById(R.id.btn_delete_2);
        btnDelete3 = view.findViewById(R.id.btn_delete_3);
        btnDelete4 = view.findViewById(R.id.btn_delete_4);
        btnDelete5 = view.findViewById(R.id.btn_delete_5);
        
        // star rating
        star1 = view.findViewById(R.id.star_1);
        star2 = view.findViewById(R.id.star_2);
        star3 = view.findViewById(R.id.star_3);
        star4 = view.findViewById(R.id.star_4);
        star5 = view.findViewById(R.id.star_5);
        
        restaurants = new ArrayList<>();
        selectedImageUris = new ArrayList<>();
        uploadedImageUrls = new ArrayList<>();
        
        setupStarClickListeners();
    }
    
    private void setupListeners() {
        btnTakePhoto.setOnClickListener(v -> checkCameraPermissionAndOpenCamera());
        btnSelectImages.setOnClickListener(v -> selectImages());
        btnSubmit.setOnClickListener(v -> submitReview());
        
        // delete button listeners
        btnDelete1.setOnClickListener(v -> deleteImage(0));
        btnDelete2.setOnClickListener(v -> deleteImage(1));
        btnDelete3.setOnClickListener(v -> deleteImage(2));
        btnDelete4.setOnClickListener(v -> deleteImage(3));
        btnDelete5.setOnClickListener(v -> deleteImage(4));
        
        // restaurant search
        etRestaurantSearch.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            selectedRestaurant = findRestaurantByName(selectedName);
        });
        
        // rating input listener
        etRatingInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(android.text.Editable s) {
                try {
                    float rating = Float.parseFloat(s.toString());
                    if (rating >= 0.0f && rating <= 5.0f) {
                        selectedRating = rating;
                        updateStarDisplay();
                    }
                } catch (NumberFormatException e) {
                    // invalid input, ignore
                }
            }
        });
    }
    
    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        reviewService = new ReviewService();
    }
    
    
    private void setupStarClickListeners() {
        ImageView[] stars = {star1, star2, star3, star4, star5};
        
        for (int i = 0; i < stars.length; i++) {
            final int starIndex = i;
            stars[i].setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    // calculate precise rating based on touch position within the star
                    float x = event.getX();
                    float starWidth = v.getWidth();
                    float clickRatio = x / starWidth;
                    
                    float baseRating = starIndex + 1;
                    float preciseRating;
                    
                    if (clickRatio < 0.2f) {
                        preciseRating = starIndex + 0.1f;
                    } else if (clickRatio < 0.4f) {
                        preciseRating = starIndex + 0.3f;
                    } else if (clickRatio < 0.6f) {
                        preciseRating = starIndex + 0.5f;
                    } else if (clickRatio < 0.8f) {
                        preciseRating = starIndex + 0.7f;
                    } else {
                        preciseRating = starIndex + 0.9f;
                    }
                    
                    selectedRating = preciseRating;
                    updateStarDisplay();
                    etRatingInput.setText(String.format("%.1f", preciseRating));
                    return true;
                }
                return false;
            });
        }
    }
    
    private void loadRestaurants() {
        db.collection("restaurants")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                restaurants.clear();
                for (com.google.firebase.firestore.DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
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
                Toast.makeText(getContext(), "Failed to load restaurants", Toast.LENGTH_SHORT).show();
            });
    }
    
    private void setupRestaurantSearch() {
        List<String> restaurantNames = new ArrayList<>();
        for (Restaurant restaurant : restaurants) {
            restaurantNames.add(restaurant.getName());
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), 
            android.R.layout.simple_dropdown_item_1line, restaurantNames);
        etRestaurantSearch.setAdapter(adapter);
    }
    
    private Restaurant findRestaurantByName(String name) {
        for (Restaurant restaurant : restaurants) {
            if (restaurant.getName().equals(name)) {
                return restaurant;
            }
        }
        return null;
    }
    
    private void updateStarDisplay() {
        ImageView[] stars = {star1, star2, star3, star4, star5};
        
        for (int i = 0; i < 5; i++) {
            // Calculate fill amount for this star: (rating - i), clamped between 0 and 1
            double fillAmount = Math.max(0.0, Math.min(1.0, selectedRating - i));
            
            // Get the drawable and check if it's a LayerDrawable
            android.graphics.drawable.Drawable drawable = stars[i].getDrawable();
            if (drawable instanceof android.graphics.drawable.LayerDrawable) {
                // Use LayerDrawable with ClipDrawable for precise fills
                android.graphics.drawable.LayerDrawable layerDrawable = (android.graphics.drawable.LayerDrawable) drawable;
                int level = (int) (fillAmount * 10000);
                android.graphics.drawable.ClipDrawable clipDrawable = (android.graphics.drawable.ClipDrawable) layerDrawable.findDrawableByLayerId(android.R.id.progress);
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
    
    private void checkCameraPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, 100);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(getContext(), "Camera permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void openCamera() {
        File photoFile = new File(requireContext().getExternalFilesDir(null), "review_photo_" + System.currentTimeMillis() + ".jpg");
        
        try {
            photoFile.createNewFile();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create photo file", e);
        }
        
        cameraImageUri = FileProvider.getUriForFile(
            requireContext(),
            requireContext().getPackageName() + ".provider",
            photoFile
        );
        
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
        intent.putExtra("android.intent.extra.videoQuality", 1);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        
        startActivityForResult(intent, TAKE_PHOTO_REQUEST);
    }
    
    private void selectImages() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Select Images"), PICK_IMAGE_REQUEST);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICK_IMAGE_REQUEST && data != null) {
                // gallery selection
            if (data.getClipData() != null) {
                    // multiple images selected
                int count = data.getClipData().getItemCount();
                    for (int i = 0; i < Math.min(count, MAX_IMAGES); i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    selectedImageUris.add(imageUri);
                }
            } else if (data.getData() != null) {
                    // single image selected
                selectedImageUris.add(data.getData());
                }
            } else if (requestCode == TAKE_PHOTO_REQUEST && cameraImageUri != null) {
                // camera capture
                selectedImageUris.add(cameraImageUri);
            }
            
            updateImagePreviews();
        }
    }
    
    
    private void deleteImage(int index) {
        if (index >= 0 && index < selectedImageUris.size()) {
            selectedImageUris.remove(index);
            updateImagePreviews();
        }
    }
    
    private void updateImagePreviews() {
        LinearLayout imagePreviewsContainer = getView().findViewById(R.id.imagePreviewsContainer);
        LinearLayout imageRow1 = getView().findViewById(R.id.imageRow1);
        LinearLayout imageRow2 = getView().findViewById(R.id.imageRow2);
        
        if (selectedImageUris.isEmpty()) {
            imagePreviewsContainer.setVisibility(View.GONE);
            return;
        }
        
        imagePreviewsContainer.setVisibility(View.VISIBLE);
        
        // clear all previews and delete buttons first
        ImageView[] previews = {ivPreview1, ivPreview2, ivPreview3, ivPreview4, ivPreview5};
        ImageView[] deleteButtons = {btnDelete1, btnDelete2, btnDelete3, btnDelete4, btnDelete5};
        
        for (int i = 0; i < previews.length; i++) {
            previews[i].setImageDrawable(null);
            previews[i].setVisibility(View.GONE);
            deleteButtons[i].setVisibility(View.GONE);
        }
        
        // show only the rows we need
        if (selectedImageUris.size() <= 3) {
            imageRow2.setVisibility(View.GONE);
        } else {
            imageRow2.setVisibility(View.VISIBLE);
        }
        
        // set previews for selected images and show delete buttons
        for (int i = 0; i < Math.min(selectedImageUris.size(), MAX_IMAGES); i++) {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), selectedImageUris.get(i));
                previews[i].setImageBitmap(bitmap);
                previews[i].setVisibility(View.VISIBLE);
                deleteButtons[i].setVisibility(View.VISIBLE);
            } catch (IOException e) {
                Log.e(TAG, "error loading image preview", e);
            }
        }
        
        // update photo count
        updatePhotoCount();
    }
    
    private void submitReview() {
        if (!validateInput()) {
            return;
        }
        
        btnSubmit.setEnabled(false);
        btnSubmit.setText("Submitting...");
        
        // Upload images first
        uploadImagesAndCreateReview();
    }
    
    private boolean validateInput() {
        if (TextUtils.isEmpty(etCaption.getText().toString().trim())) {
            Toast.makeText(getContext(), "Please enter a caption", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (TextUtils.isEmpty(etDescription.getText().toString().trim())) {
            Toast.makeText(getContext(), "Please enter a description", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (selectedRestaurant == null) {
            Toast.makeText(getContext(), "Please select a restaurant", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (selectedRating == 0.0f) {
            Toast.makeText(getContext(), "Please provide a rating", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        return true;
    }
    
    private void uploadImagesAndCreateReview() {
        if (selectedImageUris.isEmpty()) {
            createReview(new ArrayList<>());
            return;
        }
        
        uploadedImageUrls.clear();
        uploadImagesWithSupabase();
    }
    
    private void uploadImagesWithSupabase() {
        String reviewId = UUID.randomUUID().toString();
        SupabaseStorageService supabaseService = new SupabaseStorageService(requireContext());
        
        // use a simple thread for upload
        new Thread(() -> {
            try {
                for (int i = 0; i < selectedImageUris.size(); i++) {
                    Uri imageUri = selectedImageUris.get(i);
                    String fileName = "reviews/" + reviewId + "/image_" + (i + 1) + ".jpg";
                    
                    // upload to supabase - this handled in Kotlin
                    String signedUrl = supabaseService.uploadReviewImageSync(fileName, imageUri);
                    if (signedUrl != null) {
                        uploadedImageUrls.add(signedUrl);
                    } else {
                        throw new Exception("Failed to upload image " + (i + 1));
                    }
                }
                
                // all images uploaded successfully - run on main thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        createReview(uploadedImageUrls);
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error uploading images to Supabase", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Error uploading images: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                resetSubmitButton();
            });
                }
        }
        }).start();
    }
    
    private void createReview(List<String> imageUrls) {
        final String userId = mAuth.getCurrentUser().getUid();
        String userName = mAuth.getCurrentUser().getDisplayName();
        if (userName == null || userName.isEmpty()) {
            userName = mAuth.getCurrentUser().getEmail();
        }
        final String finalUserName = userName;
        
        final String caption = etCaption.getText().toString().trim();
        final String description = etDescription.getText().toString().trim();
        final float rating = selectedRating;
        
        final double accuracyPercent = 0.0; // default 0%
        
        // Determine first image type
        String firstImageType = "SQUARE"; // Default
        if (!imageUrls.isEmpty() && !selectedImageUris.isEmpty()) {
            // Get the first image to determine orientation
            Uri firstImageUri = selectedImageUris.get(0);
            firstImageType = getImageOrientation(firstImageUri);
        }
        final String finalFirstImageType = firstImageType;
        
        // Create votes map (empty initially)
        final Map<String, Map<String, Object>> votes = new HashMap<>();
        
        // Create comments list (empty initially)
        final List<com.example.food.data.Comment> comments = new ArrayList<>();
        
        // Get user profile information including avatar
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                String userAvatarUrl = null;
                if (documentSnapshot.exists()) {
                    userAvatarUrl = documentSnapshot.getString("avatarUrl");
                }
                
                Review review = new Review();
                review.setUserId(userId);
                review.setUserName(finalUserName);
                review.setUserAvatarUrl(userAvatarUrl);
                // userName and restaurantName are not stored - they will be fetched dynamically
                review.setRestaurantId(selectedRestaurant.getId());
                review.setCaption(caption);
                review.setDescription(description);
                review.setRating(rating);
                review.setAccuracyPercent(accuracyPercent);
                review.setImageUrls(imageUrls);
                review.setFirstImageType(finalFirstImageType);
                review.setCreatedAt(new Date()); // Use current date
                review.setVotes(votes);
                review.setComments(comments);
                
                // Save to Firebase
                reviewService.saveReview(review, new ReviewService.ReviewSaveCallback() {
                    @Override
                    public void onSuccess() {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Review submitted successfully!", Toast.LENGTH_SHORT).show();
                                clearForm();
                                resetSubmitButton();
                            });
                        }
                    }
                    
                    @Override
                    public void onError(Exception e) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Failed to submit review: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "Error saving review", e);
                                resetSubmitButton();
                            });
                        }
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading user profile", e);
                // Still create review without avatar URL
                Review review = new Review();
                review.setUserId(userId);
                review.setUserName(finalUserName);
                review.setUserAvatarUrl(null);
                review.setRestaurantId(selectedRestaurant.getId());
                review.setCaption(caption);
                review.setDescription(description);
                review.setRating(rating);
                review.setAccuracyPercent(accuracyPercent);
                review.setImageUrls(imageUrls);
                review.setFirstImageType(finalFirstImageType);
                review.setCreatedAt(new Date());
                review.setVotes(votes);
                review.setComments(comments);
                
                // Save to Firebase
                reviewService.saveReview(review, new ReviewService.ReviewSaveCallback() {
                    @Override
                    public void onSuccess() {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Review submitted successfully!", Toast.LENGTH_SHORT).show();
                                clearForm();
                                resetSubmitButton();
                            });
                        }
                    }
                    
                    @Override
                    public void onError(Exception e) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Failed to submit review: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "Error saving review", e);
                                resetSubmitButton();
                            });
                        }
                    }
                });
            });
    }
    
    private void clearForm() {
        etCaption.setText("");
        etDescription.setText("");
        selectedRating = 0.0f;
        updateStarDisplay();
        etRatingInput.setText("");
        etRestaurantSearch.setText("");
        selectedRestaurant = null;
        selectedImageUris.clear();
        uploadedImageUrls.clear();
        updateImagePreviews();
        updatePhotoCount();
    }
    
    private void updatePhotoCount() {
        if (tvPhotoCount != null) {
            tvPhotoCount.setText(selectedImageUris.size() + "/5");
        }
    }
    
    
    private String getImageOrientation(Uri imageUri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), imageUri);
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            
            // Calculate aspect ratio
            float aspectRatio = (float) width / height;
            
            // Determine orientation based on aspect ratio
            if (aspectRatio > 1.2f) {
                return "HORIZONTAL"; // Width is significantly larger than height
            } else if (aspectRatio < 0.8f) {
                return "PORTRAIT"; // Height is significantly larger than width
            } else {
                return "SQUARE"; // Width and height are roughly equal
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading image for orientation detection", e);
            return "SQUARE"; // Default fallback
        }
    }
    
    private void resetSubmitButton() {
        btnSubmit.setEnabled(true);
        btnSubmit.setText("Post Review");
    }
}

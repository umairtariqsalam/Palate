package com.example.food.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.food.R;
import com.example.food.data.Comment;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {
    private List<Comment> comments;
    private FirebaseFirestore db;

    public CommentsAdapter(List<Comment> comments) {
        this.comments = comments;
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);
        if (comment != null) {
            holder.bind(comment, db);
        }
    }

    @Override
    public int getItemCount() {
        return comments != null ? comments.size() : 0;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
        notifyDataSetChanged();
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        private CircleImageView ivAvatar;
        private TextView tvUserName;
        private TextView tvCommentDate;
        private TextView tvCommentText;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_comment_avatar);
            tvUserName = itemView.findViewById(R.id.tv_comment_username);
            tvCommentDate = itemView.findViewById(R.id.tv_comment_time);
            tvCommentText = itemView.findViewById(R.id.tv_comment_content);
        }

        public void bind(Comment comment, FirebaseFirestore db) {
            if (tvUserName != null) {
                tvUserName.setText(comment.getUserName());
            }
            if (tvCommentDate != null) {
                tvCommentDate.setText(comment.getFormattedDate());
            }
            if (tvCommentText != null) {
                tvCommentText.setText(comment.getText());
            }

            if (ivAvatar != null) {
                // default to icon until we know an avatar exists
                ivAvatar.setBorderWidth(0);
                ivAvatar.setImageResource(R.drawable.ic_person);

                String userId = comment.getUserId();
                if (userId == null || userId.trim().isEmpty()) {
                    return;
                }

                db.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String avatarUrl = doc.getString("avatarUrl");
                            if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                                ivAvatar.setBorderWidth(2);
                                ivAvatar.setBorderColor(ContextCompat.getColor(itemView.getContext(), R.color.logo_primary));
                                Glide.with(itemView.getContext())
                                    .load(avatarUrl)
                                    .placeholder(R.drawable.ic_person)
                                    .error(R.drawable.ic_person)
                                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                                    .centerCrop()
                                    .override(72, 72)
                                    .into(ivAvatar);
                            } else {
                                ivAvatar.setBorderWidth(0);
                                ivAvatar.setImageResource(R.drawable.ic_person);
                            }
                        } else {
                            ivAvatar.setBorderWidth(0);
                            ivAvatar.setImageResource(R.drawable.ic_person);
                        }
                    })
                    .addOnFailureListener(e -> {
                        ivAvatar.setBorderWidth(0);
                        ivAvatar.setImageResource(R.drawable.ic_person);
                    });
            }
        }
    }
}
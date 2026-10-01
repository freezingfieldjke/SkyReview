package com.skyblockrating.gui;

import com.skyblockrating.data.ReviewEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlayerRatingEntry {
    private int rank;
    private String uuid;
    private String name;
    private final String category;
    private double rating;
    private final List<ReviewEntry> reviews = new ArrayList<>();

    public PlayerRatingEntry(int rank, String name, String category, double rating, int reviewsCount, String details) {
        this(rank, null, name, category, rating, reviewsCount, details);
    }

    public PlayerRatingEntry(int rank, String uuid, String name, String category, double rating, int reviewsCount, String details) {
        this.rank = rank;
        this.uuid = (uuid != null) ? uuid.replace("-", "").toLowerCase() : null;
        this.name = name;
        this.category = category;
        this.rating = Math.min(5.0, Math.max(0.0, rating));
        if (details != null && !details.trim().isEmpty()) {
            this.reviews.add(new ReviewEntry("System", null, name, this.uuid, category, "General", rating, details));
        }
    }

    public void addOrUpdateReview(ReviewEntry review) {
        if (review == null) return;
        if (review.getTargetUuid() != null && this.uuid == null) {
            this.uuid = review.getTargetUuid();
        }
        if (review.getTargetPlayer() != null && !review.getTargetPlayer().isEmpty()) {
            this.name = review.getTargetPlayer();
        }

        Optional<ReviewEntry> existing = reviews.stream()
            .filter(r -> {
                if (review.getAuthorUuid() != null && r.getAuthorUuid() != null) {
                    return r.getAuthorUuid().equalsIgnoreCase(review.getAuthorUuid());
                }
                return r.getAuthor().equalsIgnoreCase(review.getAuthor());
            })
            .findFirst();

        existing.ifPresent(reviews::remove);
        reviews.add(review);
        recalculateRating();
    }

    public boolean removeReview(String reviewId) {
        boolean removed = reviews.removeIf(r -> r.getId().equals(reviewId));
        if (removed) {
            recalculateRating();
        }
        return removed;
    }

    private void recalculateRating() {
        if (reviews.isEmpty()) {
            this.rating = 0.0;
            return;
        }
        double sum = 0.0;
        for (ReviewEntry r : reviews) {
            sum += r.getStars();
        }
        this.rating = Math.min(5.0, Math.max(0.0, sum / reviews.size()));
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = (uuid != null) ? uuid.replace("-", "").toLowerCase() : null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.name = name;
        }
    }

    public String getCategory() {
        return category;
    }

    public double getRating() {
        return rating;
    }

    public int getReviewsCount() {
        return reviews.size();
    }

    public List<ReviewEntry> getReviews() {
        return new ArrayList<>(reviews);
    }

    public String getDetails() {
        if (reviews.isEmpty()) return "No reviews yet";
        return reviews.get(reviews.size() - 1).getComment();
    }

    public String getStarsString() {
        if (rating <= 0.0 || reviews.isEmpty()) {
            return "0.0 ☆☆☆☆☆";
        }
        int fullStars = (int) Math.floor(rating);
        boolean hasHalf = (rating - fullStars) >= 0.5;
        int emptyStars = 5 - fullStars - (hasHalf ? 1 : 0);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%.1f ", rating));
        for (int i = 0; i < fullStars; i++) {
            sb.append("★");
        }
        if (hasHalf) {
            sb.append("½");
        }
        for (int i = 0; i < emptyStars; i++) {
            sb.append("☆");
        }
        return sb.toString();
    }
}

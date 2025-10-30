# Palate

A social media platform for food lovers to discover, review, and share dining experiences across Melbourne's restaurant scene.

## Overview

Palate is an Android application that combines social media with restaurant discovery. Users can post reviews, share dining experiences, and explore Melbourne's food scene. The app uses a reputation system where active and helpful users build trust through accurate reviews and community engagement.

### Key Concepts

**Crowd Density System**: Restaurant crowding status that helps users decide when to visit. Three-tier system: Not Crowded (comfortable dining), Moderately Crowded (some wait time), Very Crowded (long wait time). Users submit current crowding status to keep information updated.

**Credibility Score**: Measures how trustworthy a user's reviews are, based on community votes, review accuracy, consistency, and engagement

**Experience Score**: Reflects a user's dining experiences, calculated from number of reviews, variety of restaurants visited, different cuisines tried, areas explored, and time active on the platform

## Technology Stack

### Frontend and UI Frameworks
- XML
- Jetpack Compose


### Backend and Services
- Java
- Kotlin
- Firebase: Authentication, Firestore database
- Supabase: Image storage

### Map Services
- Google Maps API
- Google Play Services Location

### Platform
- Android: Native Android application

## Features

### Home Page
- Browse reviews from users about restaurants in Melbourne
- Image and text display format
- Search functionality for users, reviews, and restaurants
- View detailed posts including:
  - Images and captions
  - Restaurant descriptions and ratings
  - Review accuracy scores from community feedback
  - User engagement metrics
- Vote on review accuracy
- Write and view comments on reviews

### Map
- Display locations of nearby restaurants using Google Maps
- Tap restaurant markers to view related reviews
- Get directions to restaurants
- View crowd density status at restaurants
- Users can submit current crowding status

### Add Post
- Create food review posts with text and images
- Take photos in-app or select from gallery
- Add ratings for restaurants
- Write captions and descriptions
- Tag the restaurant being reviewed
- Posts visible to other users immediately
- Delete your own posts

### Profile Page
- View username, profile picture, and bio
- Display Credibility Score with breakdown:
  - Community votes on reviews
  - Accuracy of reviews
  - Consistency over time
  - Comment engagement
- Display Experience Score with breakdown:
  - Number of reviews written
  - Variety of restaurants visited
  - Different cuisines tried
  - Areas explored
  - Time active on platform
- View detailed analytics:
  - Member duration
  - Cuisine diversity
  - Region diversity
  - Total accuracy rating
  - Community votes received
- Activity notifications:
  - When others find your reviews accurate
  - When others comment on your reviews

### Settings
- Update username and password
- Upload profile picture and add bio
- Logout functionality

## Setup Instructions

1. Clone the project repository
2. Open the project in Android Studio
3. Ensure required SDK components are installed
4. Verify Firebase (Firestore database and Authentication) is running
5. Verify Supabase storage is configured and secret key is in app folder
6. Run the application on an Android device or emulator

## Highlights

- Built with Material Design 3
- Supports multiple screen sizes
- Firebase and Supabase integration
- Google Maps integration
- Algorithm-based credibility and experience scoring
- Community voting and commenting system
- Restaurant crowding insights from users

## Benefits

**For Users**: Discover trusted dining recommendations, explore Melbourne's food scene, and build reputation as a reliable reviewer

**For Restaurants**: Quality establishments gain visibility through authentic reviews

**For the Community**: A platform where honest reviews and genuine experiences are valued

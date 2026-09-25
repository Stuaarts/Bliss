import '../marketplace/marketplace.dart';

class ServiceReview {
  const ServiceReview(this.rating, this.comment, this.createdAt);
  final int rating;
  final String comment;
  final DateTime createdAt;
  factory ServiceReview.fromJson(Json json) => ServiceReview(
    json['rating'] as int,
    json['comment'] as String,
    DateTime.parse(json['createdAt'] as String),
  );
}

class ProviderReviews {
  const ProviderReviews(this.items, this.total, this.average);
  final List<ServiceReview> items;
  final int total;
  final String? average;
  factory ProviderReviews.fromJson(Json json) => ProviderReviews(
    (json['items'] as List)
        .map((v) => ServiceReview.fromJson(v as Json))
        .toList(),
    json['total'] as int,
    json['averageRating'] as String?,
  );
}

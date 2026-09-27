import '../marketplace/marketplace.dart';

class DisputeSummary {
  DisputeSummary.fromJson(Json j)
    : id = j['id'] as String,
      title = j['title'] as String,
      status = j['status'] as String;
  final String id, title, status;
}

String disputeStatus(String value) => switch (value) {
  'COLLECTING_EVIDENCE' => 'Collecting evidence',
  'UNDER_REVIEW' => 'Waiting for admin review',
  'RESOLVED_CUSTOMER' => 'Resolved in customer’s favor',
  'RESOLVED_PROVIDER' => 'Resolved in provider’s favor',
  _ => value,
};

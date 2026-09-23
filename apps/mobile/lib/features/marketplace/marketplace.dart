import '../../core/auth/auth_session.dart';

typedef Json = Map<String, dynamic>;

class MarketPage<T> {
  const MarketPage(this.items, this.total);
  final List<T> items;
  final int total;
  factory MarketPage.read(Json json, T Function(Json) parse) => MarketPage(
    (json['items'] as List).map((e) => parse(e as Json)).toList(),
    json['total'] as int,
  );
}

class ServiceRequest {
  ServiceRequest.fromJson(Json j)
    : id = j['id'] as String,
      title = j['title'] as String,
      description = j['description'] as String,
      city = j['city'] as String,
      categoryId = j['categoryId'] as String,
      currency = j['currency'] as String,
      budget = j['budget'] as String?,
      status = j['status'] as String,
      version = j['version'] as int;
  final String id, title, description, city, categoryId, currency, status;
  final String? budget;
  final int version;
}

class Quote {
  Quote.fromJson(Json j)
    : id = j['id'] as String,
      providerId = j['providerId'] as String,
      providerName = j['providerName'] as String,
      description = j['description'] as String,
      price = j['price'] as String,
      currency = j['currency'] as String,
      status = j['status'] as String,
      expiresAt = DateTime.parse(j['expiresAt'] as String);
  final String id,
      providerId,
      providerName,
      description,
      price,
      currency,
      status;
  final DateTime expiresAt;
  bool get available =>
      status == 'SUBMITTED' && expiresAt.isAfter(DateTime.now());
}

class Booking {
  Booking.fromJson(Json j)
    : id = j['id'] as String,
      providerId = j['providerId'] as String,
      title = j['title'] as String,
      description = j['description'] as String,
      city = j['city'] as String,
      price = j['price'] as String,
      currency = j['currency'] as String,
      status = j['status'] as String,
      scheduledAt = _date(j['scheduledAt']),
      deadline = _date(j['decisionDeadline']);
  final String id,
      providerId,
      title,
      description,
      city,
      price,
      currency,
      status;
  final DateTime? scheduledAt, deadline;
  bool get terminal => ['COMPLETED', 'CANCELLED', 'EXPIRED'].contains(status);
  static DateTime? _date(Object? value) =>
      value == null ? null : DateTime.parse(value as String);
  List<String> actions({
    required bool provider,
    required bool customer,
    DateTime? now,
  }) {
    final time = now ?? DateTime.now();
    if (provider) {
      if (status == 'WAIT_PROVIDER' && (deadline?.isAfter(time) ?? false)) {
        return ['provider-accept', 'provider-reject'];
      }
      if (status == 'SCHEDULED' &&
          scheduledAt != null &&
          !time.isBefore(scheduledAt!)) {
        return ['start'];
      }
      if (status == 'IN_PROGRESS') return ['mark-complete'];
    } else if (customer) {
      if (status == 'WAIT_CUSTOMER') return ['confirm-completion'];
      if (['CREATED', 'WAIT_PROVIDER', 'SCHEDULED'].contains(status)) {
        return ['cancel'];
      }
    }
    return [];
  }
}

class MarketplaceRepository {
  const MarketplaceRepository(this.session);
  final AuthSession session;
  Future<Json> get(String path) async =>
      await session.request('GET', '/api/v1/$path') as Json;
  Future<MarketPage<Booking>> bookings(int page) async => MarketPage.read(
    await get('bookings?page=$page&size=20'),
    Booking.fromJson,
  );
  Future<MarketPage<ServiceRequest>> requests(
    int page, {
    bool opportunities = false,
  }) async => MarketPage.read(
    await get(
      '${opportunities ? 'provider/opportunities' : 'service-requests'}?page=$page&size=20',
    ),
    ServiceRequest.fromJson,
  );
  Future<MarketPage<Quote>> quotes(String path, int page) async =>
      MarketPage.read(await get('$path?page=$page&size=20'), Quote.fromJson);
  Future<List<Json>> providers() async =>
      (await session.request('GET', '/api/v1/providers/me') as List)
          .cast<Json>()
          .where((m) => m['role'] == 'OWNER')
          .toList();
  Future<List<Json>> categories() async =>
      (await session.request('GET', '/api/v1/categories') as List).cast<Json>();
}

String statusLabel(String status) => switch (status) {
  'CREATED' => 'Preparing booking',
  'WAIT_PROVIDER' => 'Waiting for provider',
  'SCHEDULED' => 'Scheduled',
  'IN_PROGRESS' => 'Service in progress',
  'WAIT_CUSTOMER' => 'Waiting for your confirmation',
  'COMPLETED' => 'Completed',
  'CANCELLED' => 'Cancelled',
  'EXPIRED' => 'Expired',
  'OPEN' => 'Open',
  'ACCEPTED' => 'Accepted',
  'SUBMITTED' => 'Submitted',
  'DECLINED' => 'Declined',
  'AUTHORIZED' => 'Authorized',
  'CAPTURED' => 'Captured',
  'VOIDED' => 'Voided',
  'REFUNDED' => 'Refunded',
  'NOT_AUTHORIZED' => 'Not yet authorized',
  _ => status,
};

String actionLabel(String action) => switch (action) {
  'provider-accept' => 'Accept and schedule',
  'provider-reject' => 'Decline booking',
  'start' => 'Start service',
  'mark-complete' => 'Mark work complete',
  'confirm-completion' => 'Confirm completion',
  'cancel' => 'Cancel booking',
  _ => action,
};

String marketError(Object e) => e is AccountRequestException
    ? e.message
    : 'Could not confirm the response. Check your connection and try again.';

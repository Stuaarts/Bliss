import '../../core/api/api_client.dart';

class Category {
  const Category({required this.id, required this.name, required this.slug});
  final String id;
  final String name;
  final String slug;

  factory Category.fromJson(Map<String, dynamic> json) => Category(
    id: json['id'] as String,
    name: json['name'] as String,
    slug: json['slug'] as String,
  );
}

class Offering {
  const Offering({
    required this.id,
    required this.title,
    required this.description,
    required this.price,
    required this.currency,
    required this.category,
    required this.providerName,
    required this.city,
    this.version = 0,
    this.providerId = '',
  });
  final String id;
  final String title;
  final String description;
  // Preserve the exact decimal API representation; never convert money to double.
  final String price;
  final String currency;
  final Category category;
  final String providerName;
  final String city;
  final int version;
  final String providerId;

  factory Offering.fromJson(Map<String, dynamic> json) {
    final provider = json['provider'] as Map<String, dynamic>;
    return Offering(
      id: json['id'] as String,
      title: json['title'] as String,
      description: json['description'] as String,
      price: json['price'] as String,
      currency: json['currency'] as String,
      category: Category.fromJson(json['category'] as Map<String, dynamic>),
      providerName: provider['displayName'] as String,
      providerId: provider['id'] as String? ?? '',
      city: provider['city'] as String,
      version: json['version'] as int? ?? 0,
    );
  }
}

class OfferingPage {
  const OfferingPage(this.items, this.total);
  final List<Offering> items;
  final int total;
}

class CatalogRepository {
  const CatalogRepository(this.api);
  final ApiClient api;

  Future<List<Category>> categories() async {
    final json = await api.get('/api/v1/categories') as List<dynamic>;
    return json
        .map((item) => Category.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<OfferingPage> offerings({String? categoryId, int page = 0}) async {
    final json = await api.get('/api/v1/offerings', {
      'page': '$page',
      'size': '20',
      'categoryId': ?categoryId,
    }) as Map<String, dynamic>;
    return OfferingPage(
      (json['items'] as List<dynamic>)
          .map((item) => Offering.fromJson(item as Map<String, dynamic>))
          .toList(),
      json['total'] as int,
    );
  }
}

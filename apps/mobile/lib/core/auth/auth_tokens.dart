abstract interface class AuthTokens {
  Future<void> restore();
  Future<void> signIn();
  Future<String?> accessToken();
  Future<void> signOut();
}

const issuerUrl = String.fromEnvironment(
  'OIDC_ISSUER',
  defaultValue: 'http://localhost:8180/realms/bliss',
);

#!/usr/bin/env bash
# Generates an RSA-2048 key pair for JWT signing (RS256).
# Output goes to paystream-auth-service/src/main/resources/keys/
# In PRODUCTION: mount these as Kubernetes Secrets, not in the classpath.

set -euo pipefail

KEYS_DIR="paystream-auth-service/src/main/resources/keys"
mkdir -p "$KEYS_DIR"

echo "Generating RSA-2048 private key..."
openssl genrsa -out "$KEYS_DIR/private-raw.pem" 2048

echo "Converting to PKCS#8 format (required by Java KeyFactory)..."
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
    -in  "$KEYS_DIR/private-raw.pem" \
    -out "$KEYS_DIR/private.pem"
rm "$KEYS_DIR/private-raw.pem"

echo "Extracting public key..."
openssl rsa -pubout \
    -in  "$KEYS_DIR/private.pem" \
    -out "$KEYS_DIR/public.pem"

echo ""
echo "Keys generated:"
echo "  Private key: $KEYS_DIR/private.pem"
echo "  Public key:  $KEYS_DIR/public.pem"
echo ""
echo "WARNING: private.pem is in .gitignore. Never commit it."
echo "For production, mount these files as Kubernetes Secrets."

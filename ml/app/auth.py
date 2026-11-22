import hmac
from fastapi import Header, HTTPException, status
from app.config import settings

INTERNAL_KEY_HEADER = "X-Internal-ML-Key"

def verify_internal_ml_key(
    x_internal_ml_key: str = Header(None, alias=INTERNAL_KEY_HEADER)
) -> bool:
    """
    Enforces internal service-to-service authentication using constant-time comparison.
    Rejects missing or invalid internal keys without exposing secrets or detailed internals.
    """
    if not x_internal_ml_key or not x_internal_ml_key.strip():
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing required internal service authentication key in header: " + INTERNAL_KEY_HEADER,
            headers={"WWW-Authenticate": INTERNAL_KEY_HEADER}
        )

    # Constant-time comparison prevents timing attacks
    is_valid = hmac.compare_digest(
        x_internal_ml_key.strip().encode("utf-8"),
        settings.internal_ml_secret.strip().encode("utf-8")
    )

    if not is_valid:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Forbidden: Invalid internal service authentication key"
        )

    return True

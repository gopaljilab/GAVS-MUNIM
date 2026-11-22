import os
from pydantic import BaseModel, Field

class Settings(BaseModel):
    service_name: str = "munim-ml-service"
    service_version: str = "0.1.0"
    feature_schema_version: str = "1.0.0"
    internal_ml_secret: str = Field(default_factory=lambda: os.getenv("INTERNAL_ML_SECRET", "munim-internal-ml-secret-dev"))
    environment: str = Field(default_factory=lambda: os.getenv("ENV", "development"))
    model_storage_path: str = Field(default_factory=lambda: os.getenv("MODEL_STORAGE_PATH", "/app/models"))

settings = Settings()

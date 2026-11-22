import logging
from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse
from app.config import settings
from app.api.health_routes import router as health_router
from app.api.status_routes import router as status_router
from app.api.prediction_routes import router as prediction_router
from app.api.dataset_routes import router as dataset_router
from app.api.evaluation_routes import router as evaluation_router

# Configure structured logging (omits secrets and confidential procurement payloads)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("munim-ml")

from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting %s v%s (Environment: %s)", settings.service_name, settings.service_version, settings.environment)
    logger.info("Active model status: NO_VALIDATED_MODEL (Inference disabled until authentic pilot evidence is collected)")
    yield

app = FastAPI(
    title="MUNIM Machine Learning Microservice",
    description="Continuous Supplier Learning & Delivery Risk Analytics for Manufacturing MSMEs",
    version=settings.service_version,
    docs_url=None if settings.environment == "production" else "/docs",
    redoc_url=None,
    lifespan=lifespan
)

# Register routers
app.include_router(health_router)
app.include_router(status_router)
app.include_router(prediction_router)
app.include_router(dataset_router)
app.include_router(evaluation_router)

@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    logger.error("Unhandled exception processing %s %s: %s", request.method, request.url.path, str(exc))
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "type": "about:blank",
            "title": "Internal Server Error",
            "status": 500,
            "detail": "An internal error occurred in the ML microservice",
            "code": "INTERNAL_ML_ERROR"
        }
    )

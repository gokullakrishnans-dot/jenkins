import logging
import os
from azure.monitor.opentelemetry import configure_azure_monitor

connection_string = os.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING")
if connection_string:
    configure_azure_monitor(connection_string=connection_string)

logger = logging.getLogger("my_app_logger")
logger.setLevel(logging.INFO)

logger.info("This is an INFO level log pushed to Application Insights.")
logger.warning("This is a WARNING log with extra context.", extra={"userId": "12345"})

try:
    1 / 0
except ZeroDivisionError:
    logger.exception("An error occurred during math operations!")

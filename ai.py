import google.generativeai as genai
import os
from datetime import datetime, timezone, timedelta
import json
import pytz
from urllib.parse import quote_plus

def maps_link_from_location(location: str) -> str:
    if not location:
        return ""
    return f"https://www.google.com/maps/search/?api=1&query={quote_plus(location)}"

def getCurrentUTCTime():
    return datetime.utcnow().replace(tzinfo=timezone.utc)

def get_local_now_string(cfg) -> str:
    utc_now = getCurrentUTCTime()

    if cfg.getTimeZone():
        local_tz = pytz.timezone(cfg.getTimeZone())
        local_now = utc_now.astimezone(local_tz)
        return local_now.strftime("%Y-%m-%d %H:%M:%S %Z")
    else:
        offset = timedelta(hours=cfg.getTimeZoneOffset())
        local_now = utc_now.astimezone(timezone(offset))
        return local_now.strftime("%Y-%m-%d %H:%M:%S UTC%z")

def parse_reminder_from_text(free_text: str, cfg) -> dict:
    """
    Returns dict with keys: Title, Description, Date (yyyy-mm-dd), Time (HH:MM:SS), Link (optional/empty)
    in the *local timezone* defined by cfg (timezone name or offset).
    """
    genai.configure(api_key=os.environ["GEMINI_API_KEY"])

    local_now = get_local_now_string(cfg)
    tz_hint = cfg.getTimeZone() if cfg.getTimeZone() else f"UTC offset {cfg.getTimeZoneOffset()} hours"

    system_instructions = f"""
You convert a user's free text into reminder fields.

Rules:
- Use timezone: {tz_hint}.
- Current local datetime: {local_now}.
- Output MUST follow the JSON schema exactly.
- Date format: yyyy-mm-dd
- Time format: HH:MM:SS (24-hour).
- If the user didn't specify seconds, use :00.
- If the user doesn't provide a link, set Link to "".
- Infer a concise Title and a helpful Description.
- If the text is ambiguous, make the best reasonable assumption (do not ask questions).
- Location is optional, and likely not provided.
- If the event is inappropriate, make Title/Description empty, and
  set Date to 2026-01-01 and Time to 00:00:00.
"""

    schema = genai.protos.Schema(
        type=genai.protos.Type.OBJECT,
        properties={
            "Title":       genai.protos.Schema(type=genai.protos.Type.STRING),
            "Description": genai.protos.Schema(type=genai.protos.Type.STRING),
            "Date":        genai.protos.Schema(type=genai.protos.Type.STRING),
            "Time":        genai.protos.Schema(type=genai.protos.Type.STRING),
            "Link":        genai.protos.Schema(type=genai.protos.Type.STRING),
            "Location":    genai.protos.Schema(type=genai.protos.Type.STRING),
        },
        required=["Title", "Description", "Date", "Time", "Link", "Location"],
    )

    model = genai.GenerativeModel(
        model_name="gemini-2.5-flash-preview-04-17",
        system_instruction=system_instructions,
        generation_config=genai.GenerationConfig(
            response_mime_type="application/json",
            response_schema=schema,
        ),
    )

    response = model.generate_content(free_text)
    data = json.loads(response.text)

    if "Location" in data and data["Location"]:
        data["Link"] = maps_link_from_location(data["Location"])

    if "Link" not in data or data["Link"] is None:
        data["Link"] = ""

    return data

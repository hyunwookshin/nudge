from openai import OpenAI
from datetime import datetime, timezone
import json
import pytz
from urllib.parse import quote_plus

def maps_link_from_location(location: str) -> str:
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

def parse_reminder_from_text_openai(free_text: str, cfg) -> dict:
    """
    Returns dict with keys: Title, Description, Date (yyyy-mm-dd), Time (HH:MM:SS), Link (optional/empty)
    in the *local timezone* defined by cfg (timezone name or offset).
    """
    client = OpenAI()

    local_now = get_local_now_string(cfg)
    tz_hint = cfg.getTimeZone() if cfg.getTimeZone() else f"UTC offset {cfg.getTimeZoneOffset()} hours"

    schema = {
        "name": "reminder_fields",
        "schema": {
            "type": "object",
            "additionalProperties": False,
            "properties": {
                "Title": {"type": "string"},
                "Description": {"type": "string"},
                "Date": {"type": "string", "pattern": r"^\d{4}-\d{2}-\d{2}$"},
                "Time": {"type": "string", "pattern": r"^\d{2}:\d{2}:\d{2}$"},
                "Link": {"type": "string"},
                "Address": {"type": "string"}
            },
            "required": ["Title", "Description", "Date", "Time", "Link", "Address"]
        }
    }

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
- Address is optional, and likely not provided
"""

    # Responses API w/ Structured Outputs (JSON schema) :contentReference[oaicite:3]{index=3}
    resp = client.responses.create(
        model="gpt-4.1-mini",
        input=[
            {"role": "system", "content": system_instructions},
            {"role": "user", "content": free_text},
        ],
        text={
            "format": {
                "type": "json_schema",
                "name": "reminder_fields",   # ✅ REQUIRED
                "schema": schema["schema"], # ✅ pass ONLY the inner schema
            }
        },
    )

    # The SDK returns the final text output as JSON text; parse it
    # (This shape can vary slightly across SDK versions; handle both common cases.)
    out_text = None
    if hasattr(resp, "output_text") and resp.output_text:
        out_text = resp.output_text
    else:
        # fallback: dig into output array
        out_text = resp.output[0].content[0].text

    data = json.loads(out_text)

    if "Address" in data and data["Address"] is not None:
        data["Link"] = maps_link_from_location(data["Address"])

    # Guarantee Link exists
    if "Link" not in data or data["Link"] is None:
        data["Link"] = ""

    return data

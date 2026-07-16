import requests
import json
question = 'What is your parameter count'

# First API call with reasoning
response = requests.post(
  url="https://openrouter.ai/api/v1/chat/completions",
  headers={
    "Authorization": "Bearer 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",
    "Content-Type": "application/json",
  },
  data=json.dumps({
    "model": "tencent/hy3:free",
    "messages": [
        {
          "role": "user",
          "content": question
        }
      ],
    "reasoning": {"enabled": True}
  })
)

# Extract the assistant message with reasoning_details
response = response.json()
response = response['choices'][0]['message']
# Preserve the assistant message with reasoning_details
messages = [
  {"role": "user", "content": question},
  {
    "role": "assistant",
    "content": response.get('content'),
    "reasoning_details": response.get('reasoning_details')  # Pass back unmodified
  },
  {"role": "user", "content": "Are you sure? Think carefully."}
]
print(response)
# Second API call - model continues reasoning from where it left off
response2 = requests.post(
  url="https://openrouter.ai/api/v1/chat/completions",
  data=json.dumps({
    "model": "tencent/hy3:free",
    "messages": messages,  # Includes preserved reasoning_details
    "reasoning": {"enabled": True}
  })
)
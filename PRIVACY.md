# CoDriver AI — Privacy Policy

_Last updated: 2026-10-27_

This is the source text of the privacy policy; the page actually published is
**`docs/index.html`**, served by GitHub Pages at:

> **https://aviramj.github.io/codriver-aa/**

Google Play requires a privacy policy URL for any app that requests the
microphone permission. See `PLAY_CONSOLE.md` → Step 3 to turn Pages on.

## Who runs this app
CoDriver AI is published for personal/testing use by the app owner. There is no
backend server operated by the app.

## What the app accesses
- **Microphone:** When you tap “Speak,” the app records your voice so your
  request can be transcribed. Recording happens only while you are actively
  using the feature.
- **The configuration you enter:** Your API key, chosen model/endpoint, and
  system prompt are stored **only on your device** (encrypted where supported).
  They are never sent anywhere except, as an API key, to the AI provider you
  configure.

## What is sent off the device, and to whom
To answer you, the app sends data to the **AI provider you configure** (by
default, OpenAI, via the base URL in Settings):

- Your recorded audio is sent for **speech-to-text transcription**.
- The transcribed text and recent conversation turns are sent to the
  **chat model** to generate a reply.
- If you enable the OpenAI voice, the reply text is sent to the provider’s
  **text-to-speech** endpoint to produce audio.

Your use of that provider is governed by **that provider’s own privacy policy
and terms** (e.g. OpenAI). The app’s owner does not separately collect, store,
or share this data.

## Data retention
The app does not maintain its own server and does not persist your conversations
off-device. The current conversation is held in memory to support follow-up
questions and is cleared when you tap “New chat” or the app is closed. Any
retention by your chosen AI provider is governed by that provider.

## Children
This app is not directed to children.

## Contact
Questions about this policy: aviram@jenik.com.

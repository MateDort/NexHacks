TapMate AI APP

an agent blind or visually impaired people can call on the phone or message in their messeaging app or whatsapp

features:
- access to an AI assistant and google searches - Gemini API
- add edit and read contacts and reminders and get notified of them by messages and phone calls
- access to google maps navigation with detailed explonation and it also can take live images to tell user where a button a door handle might be if the light is green or red at a crosswalk or if it is saved to cross
   - for this the system knows when to take an image like at a crosswalk okay lets take an image  and check if there is a light or not and tell user the light state green or red or count down and what the countdown says. and take snapshots and analyze them every 2 seconds and report if light changed or there is no car in movement. until user crosses the walk then back to navigation mode.
   - similarly when at the destination assistant can say you arrived to your destination, do you need any other help? user can say find the door. assistant helps locating it. 
- if available google web agent in headless browser - it can be like go to drury hotel website in Marietta Georgia and get a room for 2 in jan 2 - jan 5. - ones gets to the paying and information part it can send the link to the user that lead to entering those details. It can also send screenshots and short messages like entered drury hotels website and a snapshot in messages. so user knows where the agent is
- settings can be edited via phone calll or message - like language, voice, notifications - only call, only message or both or any other basic configuration or setting 
- make calls - like call this contact for me or search for this hotel name and call them. 

agents:
Main Agent 
- Emese

Sub agents:
- Reminder_Agent
- Contact_agent
- Google_Search_Agent
- Contacts_Agent
- Navigating_Agent
- Web_Agent
- Call_Agent

tech stack:
- Call and messages - Twillio
- LLM, Google Maps, Vision, Voice - Gemini package 
- the brig - ngrok
- website: html, css, java 
- deployment: railway and vercel (webisite)

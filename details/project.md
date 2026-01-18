Problem:
Current apps for visually impaired people are very simple. You can take an image, the image gets sent to LLM with a prompt describe surroundings, the LLM returns a generic answer read aloud with a google tts voice that is very robotic. 

Idea:
Making an app for visally impaired or blind people that is much more dynamic taking over actions on the phone and outside as well.

end goal of the app:
- creating an app that helps blind or visually impaired people navigate their phone and their surrandings using android accessability api and gemini api with vision. The system is an assistant who is not just triger based but pro active dynamically helping the user. 

Feautes:
- Gemini Live session/Native Audio voice
- Live LLM responds and actions by function calling
- Google Maps - for navigation
- Google search - for quick google searches
- GUI agent - for full phone access

exapmle for a success:
user can:
- ask for a nearby bakery, order an uber to that location, when uber arrives system checks for envirement changes to orbit mode and helps user to the uber car, at the location system finds the door and door handle helps if it is pull or push, navigates user to cashier or the end of the line, helps with available items and helps with ordering, then finds an empty seat and enters orbit mode to helps user get to an empty seat.

- system knows when to call which functions. Gemini is very good with it. it knows when to call google_search() and when to call GUI_agent()  

- ask system to describe surrondings and help visually impaired people find objects and navigate to them

- enjoy books with novel_reading() or comic books with comic_books()

- ask the system to open kindell books app and open greenlights by Mathhew Mcougnehy and start reading it, system will open the app and start reading from the top with novel_reading()

- ask to walk to the closest bank. System will create the route and pull up orbit mode, helping user to navigate, when close to a crosswalk (google maps knows it so system knows it) it notifies user and also checks for the lights color if green the screen will be green if red the screen is red if count down the screen is yellow, if no ligth detected it can say to the user to look right with the phone (system checks for cars) then left (same) if no cars it will show green.

UI:
- the app is a simple UI it has a big mic button in the middle that user can click to start a conversations when click 2 button appears one is to stop the conversation one to mute the users mic. 

the buttons:
- the buttons have to be big and well visible colors. the home screen is the mic button takes over 75% of the screen with a rectangle shape
- on the left top corner there is a settings button. 
- on the session window there is 2 rectangle buttons top part is stop bottom part is mute buttons they take up the whole screen. when end session button pressed it goes back to home screen

Orbit mode:
- the whole screen is blacked out except what user or system is looking for. 
example:
- user orders an uber ---> system sees that uber is 1 minute away ---> it already knows the car coming because it saw it and saved it when ordered uber ---> starts checking for cars and runs VLM or LLM to check if car matches the uber car ---> once uber found system makes a neon green box or shape where the car is and locks car so if user turns the phone to right or left the car shape stays over the car and if too left or right an arrow appears to indicate the user is in the wrong direction and also the phone gives haptic feedback to indicate the user has to turn the phone 

Techstack:
- Gemini -LLM
- Java and  python (backend)
- yolov8 -object detection 
- overshoot for live vision

for 
steps of development:

Step 1: 
- android accessability API and Gemini API as LLM can controll the whole GUI of the phone. 
- LLM takes the goal and creates steps to achieve it. after every step it runs an analysys. It checks the screen and decides on next step. 
example:
open uber app and order an uber to Life University. 

step 2: 
- update the UI to the one written down (no orbit UI yet just the homescreen and session screen)
- wire in teh assistant features written down not yet vision but everything else

after the update user should:
- be able to talk to the assistant asking questions both GUI and basic assistant questions or giving tasks and assistant can return with an answer or complete task and return with an answer 
- ask confirmation when needed
like:
- order an uber to somewhere - GUI_agent() function
confirmation:
- just to confirm did you say to life University 
answer:
- I ordered the uber for you to Life Univertity

- ask what was a result of a game google_search()
answer:
- the results was this 

- go to my chat with Helen in messenger and check what was the last message we sent to each other
answer:
in the last conversation she asked if 7pm works for the diner and you said yes
user: okay can you create a reminder for 7pm diner with helen
system inner thought no reminder function so i will call GUI_agent() and create the reminder in the reminder app.
answer:
i created the reminder

step 3: 
- basic vision implementation
- add camera access and google vision for quick envirement awarness 
- add reading agent and the functions for diferent books 


example:

Feature: The "Uber" Agentic Flow
This handles complex, multi-step tasks that require interacting with other apps.

Step 1 (Context): User asks for a destination (e.g., "Bakery"). System finds it via Google Search.

Step 2 (Action): System calls the GUI Agent function to interact with the Uber app and book the ride.

Step 3 (Data Extraction): The system "scrapes" or saves critical details (Car Model, Color, License Plate, ETA) to a database/memory.

 Solution: Bridging the "Waiting Gap"
We solved the problem of how the agent knows when the Uber arrives without the user constantly asking.

The Problem: We cannot hard-code a loop that runs forever waiting for a notification.

The Solution (Smart Prediction): The agent reads the ETA (e.g., "11 minutes"). Instead of looping, it sets a smart timer or scheduled trigger for that duration.

Proactive Recall: When the timer expires (or the "arriving" trigger hits), the system automatically wakes up, recalls the car details (Color/Plate) from the database, and switches to the "Find & Guide" mode to help the user visually locate the specific car.


add here:
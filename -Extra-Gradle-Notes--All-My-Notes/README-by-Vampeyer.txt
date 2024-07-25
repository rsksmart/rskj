
 - The build for the rjsk-core node repository wasw failing , 
 -I had learned this was from a large update from Gradle. 

- I learned and rebuilt and reinstalled gradle and reingineered the file system on the
   main  node  rjsk repository , due to an outdated gradle wrapper. 

--- This repository has a note from 2 years ago where it was updated to  
--------------
Gradle 7.4.2 

-- Again , the build failed , and then I had learned that the gradle wrapper was out of date. 
 - I had then chose the fixing of this build and breaking bug to be my main 
project for the hackathon entry.  

I have now upgraded the gradle build to 

Gradle 8.8   , with a few more detailed notes.  







----------------------------------------------- 

To develop in this repository , 

simply make your changes and then run the same commands as before 

* gradlew build   

or  

* ./gradlew build  


commands , will build in the 

* app/build/libs/app.jar file , and you should delete the build folder , 
before each build , as running the 

*gradle build  

command , will generate simply a new build folder and its needed 
contents. 




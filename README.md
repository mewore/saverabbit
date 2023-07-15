# saverabbit

A tool I made to save rabbit drawings (my [usageist](https://twitter.com/usageist) posts) and organizing them (to show
them [here](https://www.mewore.moe/rabbits)) faster.

So, what does it do? I tell it what the date and hour of the rabbit I'm going to draw is, and it pays attention to the
clipboard. When I copy any image, it displays it and waits for me to click it - when I do, it saves it in a file and
waits for the next rabbit.

## How to use

Example ([video](/screenshots/saverabbit.webm?raw=true)):
[![SaveRabbit GIF](/screenshots/saverabbit.gif?raw=true)](/screenshots/saverabbit.webm?raw=true)

To launch it, take a look at the [Releases](https://github.com/mewore/saverabbit/releases).

Or, using Java 11 or higher, build the project and run the .jar:
```shell
cd /path/to/projects
clone git@github.com:mewore/mewore-web.git
cd saverabbit
./gradlew jar
java -jar build/libs/saverabbit-1.0-SNAPSHOT.jar
```
(or get )

You can specify a directory where the images will be saved (the current directory by default):
```shell
java -jar build/libs/saverabbit-1.0-SNAPSHOT.jar --directory /path/to/images
```

You can specify what the image kind is ("rabbit" by default):
```shell
java -jar build/libs/saverabbit-1.0-SNAPSHOT.jar --type cat
```
(This only changes the title and the names of the images)

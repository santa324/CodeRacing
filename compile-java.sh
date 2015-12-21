if [ ! -f src/main/java/Runner.java ]
then
    echo Unable to find src/main/java/Runner.java > compilation.log
    exit 1
fi

rm -rf classes
mkdir classes

javac -sourcepath "src/main/java" -d classes "src/main/java/Runner.java" > compilation.log

if [ ! -f classes/Runner.class ]
then
    echo Unable to find classes/Runner.class >> compilation.log
    exit 1
fi

jar cf "./java-cgdk.jar" -C "./classes" .

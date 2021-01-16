javac ticketingsystem/LinearizationChecker.java -d bin

echo "Manifest-Version: 1.0" > bin/Version
echo "Main-Class: ticketingsystem/LinearizationChecker" >> bin/Version

jar -cvfm checker.jar bin/Version -C bin .

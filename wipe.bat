call mvn clean
del /F /Q docs.tex *.jar compilation.log MANIFEST.MF
rd /S /Q out target classes

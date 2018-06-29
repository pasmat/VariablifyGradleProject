# VariablifyGradleProject

Convert gradle project to use variables for versions instead of hardcoded ones.

## Getting Started

This is a maven project, so simply import it into Idea and enable auto-import, and you should be good to go. That's at least what I always do. Then just run it with the target folder as sole argument.

### What 

You probably have a gradle project, for me, it was and Android project, that has multiple sub modules, which I have just initialized using the IDE, Android Studio in this case, and therefore they contained bunch of hardcoded version values.

I myself was about to include a new library that depended on a newer version of one dependency, support library, or such, and was about to start increasing the version code of each module, til I thought there must be a better way

Using cross module variables defined in projects build.gradle of course.. But migrating all those hardcoded version numbers to variables would be such a hassle.

So I created this little tool, script or whatever to do this for me.

This currently, and most likely forever will only support short hand dependencies such as

```
implementation 'group:name:version'
```

And for processing these I am using regex, and the code is pretty spaghetti, after all, this was pretty much one time task.

## Acknowledgments

* Make sure everything is backed up in version control, we don't know in which funny ways this might buck up your project
* Tool automatically backs up the modified files to gradle_backup/ folder so.. if something goes wrong, you can copy files back from there


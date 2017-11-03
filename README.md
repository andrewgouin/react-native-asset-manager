Get assets (images and videos) on Android

# Install
```
npm i --save https://github.com/andrewgouin/react-native-asset-manager/tarball/master
```

Add to settings.gradle
```javascript
include ':react-native-asset-manager'
project(':react-native-asset-manager').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-asset-manager/android')
```

Add to /app/build.gradle dependencies
```javascript
  compile project(':react-native-asset-manager')
```

Add to MainApplication.java
```java
import com.agouin.assetmanager.RNAssetManagerPackage;
...
  public List<ReactPackage> getPackages() {
    return Arrays.<ReactPackage>asList(
    new RNAssetManagerPackage(),
    ...
     );
  }
...
```

# Usage
This functions in a manner similar to react-native CameraRoll
```java
import AssetManager from 'react-native-asset-manager';
...
const numberOfAssetsPerFetch = 42;
getMoreAssets(){
    this.lastCursor = null;
    AssetManager.getAssets(numberOfAssetsPerFetch, this.lastCursor).then((r)=>{
        this.lastCursor = r.page_info.end_cursor;
        // assets are in r.edges
        // thumbnail for videos and images in r.edges[i].node.image.thumb
    });
}
...
```

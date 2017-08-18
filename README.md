Get assets (images and videos) from Android

#Install
```
npm i --save https://github.com/andrewgouin/react-native-asset-manager/tarball/master
```

#Usage
This functions in a manner similar to react-native CameraRoll
```
import AssetManager from 'react-native-asset-manager';
...
const numberOfAssetsPerFetch = 42;
getMoreAssets(){
    this.lastCursor = null;
    AssetManager.getAssets(numberOfAssetsPerFetch, this.lastCursor).then((r)=>{
        this.lastCursor = r.page_info.end_cursor;
        // photos are in r.edges
        // thumbnail for videos and images in r.edges[i].node.image.thumb
    });
}
...
```

import * as T from 'three';
import {GLTFLoader} from './vendor/three/GLTFLoader.js';
export async function loadRealisticAvatar(){
 const base=new URL('./vendor/rocket/',import.meta.url).href;
 const textures=new T.TextureLoader();
 const names=['body_color','head_color','opacity_color','body_normal','head_normal'];
 const [gltf,...maps]=await Promise.all([new GLTFLoader().loadAsync(base+'ari-detailed.glb'),...names.map(n=>textures.loadAsync(base+'f001_'+n+'.webp'))]);
 const lookup=Object.fromEntries(names.map((name,i)=>[name,maps[i]]));for(const name of names){const map=lookup[name];map.flipY=true;map.colorSpace=name.endsWith('color')?T.SRGBColorSpace:T.NoColorSpace;map.anisotropy=4;}
 const root=gltf.scene;root.traverse(o=>{if(!o.isMesh)return;o.frustumCulled=false;const label=o.material.name,type=label.includes('head')?'head':label.includes('opacity')?'opacity':'body';o.material=new T.MeshPhysicalMaterial({name:label,map:lookup[type+'_color'],normalMap:lookup[type+'_normal']||null,normalScale:new T.Vector2(.35,.35),color:0xffffff,roughness:type==='head'?.57:.64,metalness:0,clearcoat:type==='head'?.04:0,clearcoatRoughness:.65,side:type==='opacity'?T.DoubleSide:T.FrontSide,alphaTest:type==='opacity'?.4:0});if(type==='body')root.userData.body=o});root.updateMatrixWorld(true);return root;
}

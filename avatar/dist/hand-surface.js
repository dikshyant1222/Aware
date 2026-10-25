import * as T from 'three';
// Skin-aware Loop refinement. Bone numbers are categorical: merge influence
// weights instead of averaging skinIndex values (which would corrupt the rig).
export function refineHands(mesh,passes=1){
 for(let pass=0;pass<passes;pass++){
 const g=mesh.geometry,p=g.attributes.position,uv=g.attributes.uv,si=g.attributes.skinIndex,sw=g.attributes.skinWeight,ix=g.index;
 if(!ix||!si)return;
 const handBones=new Set(mesh.skeleton.bones.map((b,i)=>/Hand/.test(b.name)?i:-1).filter(i=>i>=0));
 const verts=[],groups=new Map(),groupOf=[];
 for(let i=0;i<p.count;i++){
 const pos=[p.getX(i),p.getY(i),p.getZ(i)],key=pos.map(n=>Math.round(n*1e5)).join(',');
 let id=groups.get(key);if(id===undefined){id=groups.size;groups.set(key,id)}groupOf[i]=id;
 const weights=new Map();let hand=0;for(let k=0;k<4;k++){const bi=si.array[i*4+k],w=sw.array[i*4+k];if(w){weights.set(bi,w);if(handBones.has(bi))hand+=w}}
 verts.push({pos,uv:uv?[uv.getX(i),uv.getY(i)]:[0,0],weights,hand});}
 const representative=[],neighbors=Array.from({length:groups.size},()=>new Set()),edges=new Map();
 groupOf.forEach((id,i)=>{representative[id]??=i});
 for(let i=0;i<ix.count;i+=3){const t=[ix.getX(i),ix.getX(i+1),ix.getX(i+2)];for(let k=0;k<3;k++){const a=groupOf[t[k]],b=groupOf[t[(k+1)%3]],o=groupOf[t[(k+2)%3]];neighbors[a].add(b);neighbors[b].add(a);const key=a<b?a+','+b:b+','+a;if(!edges.has(key))edges.set(key,[]);edges.get(key).push(o)}}
 function blend(stencil){const out={pos:[0,0,0],uv:[0,0],weights:new Map(),hand:0};for(const [i,w]of stencil){const v=verts[i];for(let k=0;k<3;k++)out.pos[k]+=v.pos[k]*w;for(let k=0;k<2;k++)out.uv[k]+=v.uv[k]*w;out.hand+=v.hand*w;for(const [b,bw]of v.weights)out.weights.set(b,(out.weights.get(b)||0)+bw*w)}return out}
 const result=verts.map((v,i)=>{if(v.hand<.3)return v;const ns=[...neighbors[groupOf[i]]],n=ns.length;if(n<3)return v;const beta=n===3?3/16:3/(8*n);const r=blend([[i,1-n*beta],...ns.map(id=>[representative[id],beta])]);r.uv=v.uv;return r});
 const edgeVertex=new Map(),indices=[],newGroups=[];
 function midpoint(a,b){const key=a<b?a+','+b:b+','+a;if(edgeVertex.has(key))return edgeVertex.get(key);const ga=groupOf[a],gb=groupOf[b],opp=edges.get(ga<gb?ga+','+gb:gb+','+ga)||[];let r;if(Math.min(verts[a].hand,verts[b].hand)>.3&&opp.length===2){r=blend([[a,.375],[b,.375],[representative[opp[0]],.125],[representative[opp[1]],.125]]);r.uv=[(verts[a].uv[0]+verts[b].uv[0])/2,(verts[a].uv[1]+verts[b].uv[1])/2]}else r=blend([[a,.5],[b,.5]]);const id=result.length;result.push(r);edgeVertex.set(key,id);return id}
 for(const group of (g.groups.length?g.groups:[{start:0,count:ix.count,materialIndex:0}])){const start=indices.length;for(let i=group.start;i<group.start+group.count;i+=3){const a=ix.getX(i),b=ix.getX(i+1),c=ix.getX(i+2);if(Math.max(verts[a].hand,verts[b].hand,verts[c].hand)<.05){indices.push(a,b,c);continue}const ab=midpoint(a,b),bc=midpoint(b,c),ca=midpoint(c,a);indices.push(a,ab,ca,ab,b,bc,ca,bc,c,ab,bc,ca)}newGroups.push({start,count:indices.length-start,materialIndex:group.materialIndex})}

 const positions=[],uvs=[],skinIndices=[],skinWeights=[];
 for(const v of result){positions.push(...v.pos);uvs.push(...v.uv);const weights=[...v.weights].sort((a,b)=>b[1]-a[1]).slice(0,4);while(weights.length<4)weights.push([0,0]);const total=weights.reduce((s,p)=>s+p[1],0)||1;skinIndices.push(...weights.map(p=>p[0]));skinWeights.push(...weights.map(p=>p[1]/total))}
 const next=new T.BufferGeometry();next.setAttribute('position',new T.Float32BufferAttribute(positions,3));next.setAttribute('uv',new T.Float32BufferAttribute(uvs,2));next.setAttribute('skinIndex',new T.Uint16BufferAttribute(skinIndices,4));next.setAttribute('skinWeight',new T.Float32BufferAttribute(skinWeights,4));next.setIndex(indices);for(const group of newGroups)next.addGroup(group.start,group.count,group.materialIndex);next.computeVertexNormals();next.computeBoundingSphere();mesh.geometry=next;if(pass>0)g.dispose();
 }
}
export function isolateRightHand(mesh){const g=mesh.geometry,si=g.attributes.skinIndex,sw=g.attributes.skinWeight;const selected=new Set(mesh.skeleton.bones.map((b,i)=>/^RightHand/.test(b.name)?i:-1).filter(i=>i>=0));const hand=new Float32Array(si.count);for(let i=0;i<si.count;i++)for(let k=0;k<4;k++)if(selected.has(si.array[i*4+k]))hand[i]+=sw.array[i*4+k];const indices=[];for(let i=0;i<g.index.count;i+=3){const a=g.index.getX(i),b=g.index.getX(i+1),c=g.index.getX(i+2);if(Math.min(hand[a],hand[b],hand[c])>.48)indices.push(a,b,c)}const geo=g.clone();geo.setIndex(indices);geo.clearGroups();const detail=new T.SkinnedMesh(geo,Array.isArray(mesh.material)?mesh.material[0]:mesh.material);detail.bind(mesh.skeleton,mesh.bindMatrix);detail.frustumCulled=false;detail.matrixAutoUpdate=false;detail.userData.source=mesh;return detail}

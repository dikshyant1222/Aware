import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Bone,Quaternion,Vector3} from '../app/src/main/assets/avatar/vendor/three/three.module.js';
import {PoseTransition} from '../app/src/main/assets/avatar/pose-transition.js';
import {compileMessage} from '../app/src/main/assets/avatar/common-signs.js';

const rotation=angle=>new Quaternion().setFromAxisAngle(new Vector3(0,1,0),angle);

test('clip handoff starts from the displayed pose even after mixer bindings reset',()=>{
  const bone=new Bone();bone.quaternion.copy(rotation(.7));
  const blend=new PoseTransition([bone]);
  bone.quaternion.identity();
  assert.ok(new Quaternion().fromArray(blend.renderedRotation(bone)).angleTo(rotation(.7))<1e-6);
  blend.begin();
  bone.quaternion.copy(rotation(2));
  blend.apply(1/60);
  assert.ok(bone.quaternion.angleTo(rotation(.7))<1e-6);
});

test('blend moves continuously to the solved target without changing sign targets',()=>{
  const bone=new Bone(),blend=new PoseTransition([bone],.18),target=rotation(1.3);
  blend.begin();
  let previous=bone.quaternion.clone(),progress=0;
  for(let frame=0;frame<15;frame++){
    bone.quaternion.copy(target);blend.apply(1/60);blend.captureRendered();
    assert.ok(Math.abs(bone.quaternion.length()-1)<1e-6);
    assert.ok(previous.angleTo(bone.quaternion)<.23);
    const next=rotation(0).angleTo(bone.quaternion);
    assert.ok(next>=progress-1e-6);
    previous.copy(bone.quaternion);progress=next;
  }
  assert.equal(blend.active,false);
  assert.ok(bone.quaternion.angleTo(target)<1e-6);
});

test('a new word during blending starts at the latest displayed pose',()=>{
  const bone=new Bone(),blend=new PoseTransition([bone]);
  blend.begin();
  for(let frame=0;frame<5;frame++){
    bone.quaternion.copy(rotation(1.5));blend.apply(1/60);blend.captureRendered();
  }
  const displayed=bone.quaternion.clone();
  blend.begin();bone.quaternion.copy(rotation(-1));blend.apply(1/60);
  assert.ok(bone.quaternion.angleTo(displayed)<1e-6);
  blend.reset();assert.equal(blend.active,false);
});

test('streamed words keep their final handshape and omit repeated rest motions',()=>{
  for(const text of ['Hello','thank you','world','Zoe','No']){
    const standalone=compileMessage(text,false,{live:true});
    const continuous=compileMessage(text,false,{live:true,continuous:true});
    assert.ok(continuous.clip.duration<standalone.clip.duration,text);
    assert.ok(continuous.clip.duration>0,text);
    const last=continuous.cues.at(-1);
    assert.ok(last && last.right!=='RELAXED',text);
    assert.ok(Math.abs(last.end-continuous.clip.duration)<.001,text);
  }
});

test('contractions are fingerspelled without inserting an artificial word pause',()=>{
  const result=compileMessage("Zoe's",false,{live:true,continuous:true});
  assert.deepEqual(result.spelled,['ZOES']);
  assert.deepEqual(result.marks.map(mark=>mark.letter),['Z','O','E','S']);
  assert.ok(Math.abs(result.marks[0].end-result.marks[1].start)<.001);
});

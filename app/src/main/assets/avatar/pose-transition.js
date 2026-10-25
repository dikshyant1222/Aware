// Blend the final solved pose, including IK and finger cues. Mixer-only fades
// cannot smooth joints that those solvers overwrite after the mixer update.
export class PoseTransition {
  constructor(bones, duration = .18) {
    this.duration = duration;
    this.joints = bones.map(bone => ({
      bone,
      shown: bone.quaternion.clone(),
      from: bone.quaternion.clone(),
      target: bone.quaternion.clone(),
    }));
    this.elapsed = duration;
    this.byBone = new Map(this.joints.map(joint => [joint.bone, joint]));
    this.active = false;
    this.count = 0;
  }

  begin() {
    for (const joint of this.joints) joint.from.copy(joint.shown);
    this.elapsed = 0;
    this.active = true;
    this.count++;
  }

  apply(delta) {
    if (!this.active) return;
    const u = Math.min(1, this.elapsed / this.duration);
    // Quintic easing has zero velocity and acceleration at both endpoints.
    const weight = u * u * u * (10 + u * (-15 + 6 * u));
    for (const {bone, from, target} of this.joints) {
      target.copy(bone.quaternion);
      bone.quaternion.copy(from).slerp(target, weight);
    }
    this.elapsed += Math.max(0, delta);
    if (u === 1) this.active = false;
  }

  captureRendered() {
    for (const joint of this.joints) joint.shown.copy(joint.bone.quaternion);
  }

  renderedRotation(bone) {
    return this.byBone.get(bone)?.shown.toArray();
  }

  reset() {
    this.active = false;
    this.elapsed = this.duration;
    this.captureRendered();
  }
}

# Generates feature metadata from the application calculators.
from pathlib import Path
import re,json,hashlib
ROOT=Path(__file__).resolve().parents[1];P=ROOT/'app/src/main/java/com/robotkinematicslab/mobile';D=P/'ml/data'
reader=(D/'ScientificDatasetTrainingReader.kt').read_text();expanded=(D/'ExpandedContextFeatureCalculator.kt').read_text();research=(D/'ResearchContextFeatureCalculator.kt').read_text()
def between(s,a,b):return s.split(a,1)[1].split(b,1)[0]
def splitexpr(s):
 parts=[];start=0;depth=0;quote=False;esc=False
 for i,c in enumerate(s):
  if quote:
   if c=='"' and not esc:quote=False
   esc=c=='\\' and not esc
  elif c=='"':quote=True
  elif c in '([{':depth+=1
  elif c in ')]}':depth-=1
  elif c==',' and depth==0:parts.append(' '.join(s[start:i].split()));start=i+1
 parts.append(' '.join(s[start:].split()));return [x for x in parts if x]
def quotes(s):return re.findall(r'"([a-z0-9_]+)"',s)
enames=quotes(between(expanded,'private val GLOBAL_FEATURE_NAMES = listOf(', '\n    )'))
eexpr=splitexpr(between(expanded,'values += listOf(', '\n        )'))
esuffix=quotes(between(expanded,'private val JOINT_FEATURE_SUFFIXES = listOf(', '\n    )'))
ejexpr=splitexpr(between(expanded,'val activeTheta = if (revolute) input.seeds[index] else input.theta[index]\n                values += listOf(', '\n                )'))
rnames=quotes(between(research,'private val GLOBAL_NAMES =','private val JOINT_SUFFIXES'))
rexpr=splitexpr(between(research,'values += listOf(', '\n        )'))
rsuffix=quotes(between(research,'private val JOINT_SUFFIXES =','private const val EPS'))
rjexpr=splitexpr(between(research,'val extentShare = extents[index] / reach\n                values += listOf(', '\n                )'))
assert len(enames)==len(eexpr)==103
# Intermediate definitions from the feature calculators. A card includes the transitive
# closure of symbols used by its own expression, not an unrelated family-wide formula.
COMMON={
'count':'jointTypes.size', 'EPS':'1e-12', 'LIMIT_EPS':'1e-6','CONDITION_CAP':'1e12','HEADROOM_CAP':'1e6',
'spans':'maximums[j] - minimums[j] for each present j',
'halfSpans':'max(spans[j] * 0.5, EPS)', 'centers':'(minimums[j] + maximums[j]) * 0.5',
'reach':'sum_j hypot(a[j], if jointTypes[j] is PRISMATIC then max(abs(minimums[j]),abs(maximums[j])) else abs(d[j])); floored at EPS',
'kinematics':'standard-DH seed FK with jointTypes: for REVOLUTE active theta=seeds[j], d=d[j]; for PRISMATIC theta=theta[j], active d=seeds[j]. Accumulate R and p; local rotation uses active theta and alpha[j]: rows(cos(theta),-sin(theta)*cos(alpha),sin(theta)*sin(alpha)); (sin(theta),cos(theta)*cos(alpha),-cos(theta)*sin(alpha)); (0,sin(alpha),cos(alpha)). Local translation (a*cos(active theta),a*sin(active theta),active d). Jacobian columns: pre-joint z axis for PRISMATIC, z cross (endEffector - pre-joint origin) for REVOLUTE',
'error':'(targetX,targetY,targetZ) - kinematics.endEffector', 'errorNorm':'hypot(hypot(error[0],error[1]),error[2])',
'errorDirection':'error / errorNorm if errorNorm > EPS; otherwise (0,0,0)',
'gram':'J_scaled * transpose(J_scaled), J_scaled column j = kinematics.jacobian column j * halfSpans[j]',
'singularValues':'descending sqrt(max(0,eigenvalue(gram))); symmetric Jacobi rotation solver, at most32 sweeps, off-diagonal convergence EPS',
'sigmaMax':'singularValues[0]', 'sigmaMiddle':'singularValues[1]', 'sigmaThird':'singularValues[2]',
'expectedRank':'clamp(count,1,3)', 'sigmaExpectedMin':'singularValues[expectedRank-1]',
'condition':'sigmaMax / sigmaExpectedMin if sigmaExpectedMin > EPS, else CONDITION_CAP',
'reciprocalCondition':'1 / condition if condition > EPS, else0',
'spectralEnergy':'sum(singularValues^2)', 'spectralWeights':'singularValues^2 / spectralEnergy if spectralEnergy > EPS, else three zeros',
'spectralEntropy':'-sum(w*ln(w) for w in spectralWeights when w>EPS)', 'effectiveRank':'exp(spectralEntropy)',
'rankThreshold':'max(EPS,sigmaMax*1e-9)', 'observedRank':'count(singularValues > rankThreshold)',
'manipulability':'product(first expectedRank singularValues)',
'singularityMultiplier':'10 if sigmaExpectedMin<=1e-5 or condition>=1e5; else3 if sigmaExpectedMin<=1e-3 or condition>=1e3; else1',
'lambda':'max(damping*singularityMultiplier,errorNorm*0.5,EPS)',
'dls':'solve (gram + lambda^2 I) taskStep=error by pivoted3x3 elimination (diagonals <=EPS replaced by EPS); rawJointStep[j]=halfSpans[j]^2 * dot(kinematics.jacobian column j,taskStep); rawJointStepNorm=stableNorm(rawJointStep); normalizedStepNorm=stableNorm(rawJointStep[j]/halfSpans[j])',
'lowerMargins':'(seeds[j]-minimums[j])/spans[j]', 'upperMargins':'(maximums[j]-seeds[j])/spans[j]',
'nearestMargins':'min(lowerMargins[j],upperMargins[j])', 'signedPositions':'(seeds[j]-centers[j])/halfSpans[j]',
'barriers':'-ln(max(nearestMargins[j],LIMIT_EPS))',
'directionalHeadrooms':'available travel toward sign(dls.rawJointStep[j]) divided by abs(step); available=maximums[j]-seeds[j] for step>=0 else seeds[j]-minimums[j]; return HEADROOM_CAP when abs(step)<=EPS',
'normalizedSteps':'dls.rawJointStep[j]/halfSpans[j]',
'clippedScale':'maxStep/dls.normalizedStepNorm if norm>maxStep and norm>EPS, else1',
'clippedJointStep':'dls.rawJointStep * clippedScale', 'predictedCartesianStep':'kinematics.jacobian * clippedJointStep',
'predictedResidual':'error - predictedCartesianStep', 'predictedStepNorm':'norm3(predictedCartesianStep)', 'predictedResidualNorm':'norm3(predictedResidual)',
'alignment':'dot(error,predictedCartesianStep)/(errorNorm*predictedStepNorm) if both norms>EPS, else0',
'directionalMobilitySquared':'transpose(errorDirection)*gram*errorDirection','directionalMobility':'sqrt(max(0,directionalMobilitySquared))',
'towardNearerLimit':'1 if dls.rawJointStep[j] points toward nearer margin min(lowerMargins[j],upperMargins[j]) (lower margin wins ties), otherwise0; averaged over present joints',
'staticExtents':'hypot(a[j],d[j])', 'activeExtents':'hypot(a[j],if PRISMATIC then seeds[j] else d[j]) using jointTypes[j]',
'totalExtent':'max(sum(staticExtents),EPS)', 'maxExtent':'max(staticExtents)', 'minExtent':'min(staticExtents)',
'extentShares':'staticExtents/totalExtent','extentEntropy':'-sum(share*ln(share) for extentShares>EPS)',
'innerRadialBound':'max(0,maxExtent-(totalExtent-maxExtent))',
'totalAbsA':'sum(abs(a))','totalAbsD':'sum(abs(d))','alphaSin':'sin(alpha[j])','alphaCos':'cos(alpha[j])',
'prismaticFlags':'1 for jointTypes[j] PRISMATIC else0','typeTransitions':'number of adjacent unequal prismaticFlags','longestTypeRun':'longest consecutive run of equal prismaticFlags',
'normalizedHome':'(homes[index]-centers[index])/halfSpans[index]', 'normalizedSeedHome':'(seeds[index]-homes[index])/halfSpans[index]',
'revolute':'jointTypes[index] is REVOLUTE','activeTheta':'seeds[index] if revolute else theta[index]',
'safeLog':'ln(clamp(value,EPS,CONDITION_CAP)); natural logarithm of implementation numeric value',
'rms':'stableNorm(values)/sqrt(max(number of values,1)); stableNorm folds hypot',
'standardDeviation':'population standard deviation: stableNorm(values-mean)/sqrt(number of values);0 if fewer than2',
'harmonicMean':'number of values / sum(1/max(value,LIMIT_EPS))',
'safeDirectionComponent':'atan2(targetY,targetX), then sine/cosine;0 when hypot(targetX,targetY)<=EPS',
'safeElevationComponent':'atan2(targetZ,targetCylindricalRadius), then sine/cosine;0 when hypot(radius,targetZ)<=EPS',
'norm3':'hypot(hypot(x,y),z)', 'targetRadius':'norm3(targetX,targetY,targetZ)', 'targetCylindricalRadius':'hypot(targetX,targetY)'
}
RESEARCH=COMMON|{
'reach':'max(sum(extents),EPS); seed-posture extent sum, not the conservative reach based on prismatic limits',
'pressure':'abs((seeds[j]-centers[j])/halfSpans[j]).coerceIn(-1,1)',
'margins':'(1-pressure[j])*0.5','homeDisplacement':'clamp((seeds[j]-homes[j])/halfSpans[j],-2,2)',
'extents':'hypot(a[j],if jointTypes[j] PRISMATIC then seeds[j] else d[j])',
'maximumExtent':'max(extents)', 'innerBound':'max(0,maximumExtent-(reach-maximumExtent))',
'utilization':'targetRadius/reach','outerPressure':'max(0,utilization)','innerPressure':'max(0,(innerBound-targetRadius)/reach)',
'nearestBoundaryDistance':'min(abs(targetRadius-innerBound),abs(reach-targetRadius))/reach',
'pressureSquares':'pressure^2','pressureFourths':'pressure^4','dominantLink':'maximumExtent/reach','innerVoidFraction':'innerBound/reach',
'pressureSquared':'pressureSquares[index]','displacementSquared':'homeDisplacement[index]^2','extentShare':'extents[index]/reach',
'MARGIN_FLOOR':'1e-4','square':'value*value','gini':'sum_i sum_j abs(extents[i]-extents[j])/(2*count^2*mean(extents));0 if mean<=EPS or empty'
}
INPUTS={'jointTypes':'jointTypes','theta':'dhThetaRad','d':'dhDMeters','a':'dhAMeters','alpha':'dhAlphaRad','minimums':'jointMinValues','maximums':'jointMaxValues','homes':'jointHomeValues','seeds':'seedJointValues','targetX':'targetX','targetY':'targetY','targetZ':'targetZ','damping':'ikDamping','maxStep':'ikMaxStep'}
def closure(expr,aliases):
 seen=set();ordered=[];deps=set()
 def visit(e):
  for token in re.findall(r'\b[A-Za-z][A-Za-z0-9_]*\b',e):
   if token in INPUTS:deps.add(INPUTS[token])
   if token in aliases and token not in seen:
    seen.add(token);ordered.append(token);visit(aliases[token])
 visit(expr)
 return expr+'; '+ '; '.join(f'{k} = {aliases[k]}' for k in ordered),sorted(deps)
def human(id):
 words=id.replace('research_v2_','Research v2 ').replace('_',' ').split()
 caps={'dh':'DH','dls':'DLS','ik':'IK','rms':'RMS','l2':'L2','dof':'DOF','x':'x','y':'y','z':'z'}
 return ' '.join(caps.get(w,w) for w in words).capitalize().replace('Dh','DH').replace('Dls','DLS').replace('Ik ','IK ')
VALID='Only finite, dimensionally matched inputs with positive joint spans, valid joint types, seed/home inside limits and zero active DH fields are admitted. Encoder values are Float; overflow after conversion rejects the row. Classifier training normalization is a separate train-fitted operation, clipped to [-8,8], not this feature formula.'
BASE_SOURCE='app/src/main/java/com/robotkinematicslab/mobile/ml/data/ScientificDatasetTrainingReader.kt'
EXP_SOURCE='app/src/main/java/com/robotkinematicslab/mobile/ml/data/ExpandedContextFeatureCalculator.kt'
RES_SOURCE='app/src/main/java/com/robotkinematicslab/mobile/ml/data/ResearchContextFeatureCalculator.kt'
IK_SOURCE='app/src/main/java/com/robotkinematicslab/mobile/ml/ik/OneMicronIkFeatureEncoder.kt'
entries=[]
def add(id,title,family,definition,expr,units,deps,meaning,limits,source,possible=None,aliases=None):
 if aliases is not None:
  expr,actual=closure(expr,aliases);deps=actual
 if re.match(r'(research_v2_)?joint_[0-9]+_',id) and 'jointCount' not in deps:deps=deps+['jointCount']
 if not deps:raise ValueError(('no deps',id))
 entries.append(dict(index=len(entries)+1,technicalId=id,humanName=title,family=family,definition=definition,origin='Encoded before IK from scientific CSV inputs. '+('Recomputed by standard-DH seed kinematics and mathematical transforms; not a stored output label.' if aliases else 'Read or transformed from the named CSV columns.'),calculation=expr,units=units,dependencies=deps,meaning=meaning,possibleUse=possible or 'Candidate input for testing whether '+title.lower()+' helps held-out solver-outcome discrimination; incremental usefulness must be measured by paired ablation.',limitations=limits+' '+VALID,implementationSource=source,formulaVersion='feature-dictionary-v1'))
base=[
('joint_count_ratio','Joint count divided by ten','jointCount / 10','dimensionless',['jointCount'],'Actual declared joint count as a fraction of the fixed ten-slot input capacity.','1..10 supported; not an estimate of independent Cartesian DOFs.'),
('target_x','Target x coordinate','targetX','m',['targetX'],'Requested base-frame Cartesian x coordinate.','Position only; does not encode target orientation.'),
('target_y','Target y coordinate','targetY','m',['targetY'],'Requested base-frame Cartesian y coordinate.','Position only; does not encode target orientation.'),
('target_z','Target z coordinate','targetZ','m',['targetZ'],'Requested base-frame Cartesian z coordinate.','Position only; does not encode target orientation.'),
('ik_iteration_budget_ratio','IK iteration budget divided by ten thousand','ikMaxIterations / 10000.0','dimensionless',['ikMaxIterations'],'Requested positive solver iteration ceiling rescaled by a constant.','This is the configured budget, not iterations actually consumed.'),
('log_ik_tolerance','Natural log of IK position tolerance','ln(max(ikToleranceMeters,1e-12))','log of numeric metres',['ikToleranceMeters'],'Natural logarithm of the positive requested positional stopping tolerance.','The classifier floors values below1e-12; Verified IK does not apply this floor.'),
('log_ik_damping','Natural log of requested IK damping','ln(max(ikDamping,1e-12))','log of solver damping numeric value',['ikDamping'],'Natural logarithm of configured initial damping.','Damping is a solver control, not a measured physical property; values below1e-12 are floored.'),
('log_ik_max_step','Natural log of maximum normalized IK step','ln(max(ikMaxStep,1e-12))','log of dimensionless step control',['ikMaxStep'],'Natural logarithm of requested normalized step ceiling.','Configured ceiling, not the achieved joint displacement; floor1e-12.')]
for id,title,expr,units,deps,definition,limit in base:add(id,title,'Raw task and solver inputs',definition,expr,units,deps,definition,limit,BASE_SOURCE+':306–315 · encode')
bs=[('present','presence indicator','1 if j < jointCount else0','indicator0/1',['jointCount'],'Distinguishes a real joint from padded input slots.','Zero means absent; numeric zeros in other slots alone cannot establish absence.'),('is_prismatic','prismatic type indicator','1 if present and jointTypes[j]==PRISMATIC else0','indicator0/1',['jointCount','jointTypes'],'Encodes prismatic versus revolute type for a present joint.','Zero may mean revolute or absent; inspect presence.'),('dh_theta','static DH theta angle','dhThetaRad[j]','rad',['dhThetaRad'],'Static theta in the standard-DH row.','For REVOLUTE this active DH field must be zero: the seed supplies rotation; for PRISMATIC it is a fixed angle.'),('dh_d','static DH d offset','dhDMeters[j]','m',['dhDMeters'],'Static d translation along the preceding DH z axis.','For PRISMATIC this active DH field must be zero: the seed supplies translation; for REVOLUTE it is fixed.'),('dh_a','DH a link distance','dhAMeters[j]','m',['dhAMeters'],'Signed standard-DH link a translation; retained exactly, not an absolute length.','DH parameter alone is not overall reach and depends on the declared frame convention.'),('dh_alpha','DH alpha twist','dhAlphaRad[j]','rad',['dhAlphaRad'],'Static standard-DH twist angle between successive z axes.','Periodic angle is kept raw in this baseline slot.'),('minimum','lower joint limit','jointMinValues[j]','rad for REVOLUTE; m for PRISMATIC',['jointMinValues','jointTypes'],'Lower allowed coordinate of this joint.','Must be strictly below maximum; unit depends on joint type.'),('maximum','upper joint limit','jointMaxValues[j]','rad for REVOLUTE; m for PRISMATIC',['jointMaxValues','jointTypes'],'Upper allowed coordinate of this joint.','Must exceed minimum; not an unconstrained travel capability.'),('home','declared home coordinate','jointHomeValues[j]','rad for REVOLUTE; m for PRISMATIC',['jointHomeValues','jointTypes'],'Saved reference/home coordinate, constrained inside limits.','Home is not necessarily the initial seed or a physical calibrated zero.'),('seed','initial solver coordinate','seedJointValues[j]','rad for REVOLUTE; m for PRISMATIC',['seedJointValues','jointTypes'],'Initial coordinate supplied to IK before any iteration.','This is a joint-state seed, not the random-number generator seed or solved coordinate.')]
for j in range(1,11):
 for suffix,title,expr,unit,deps,definition,limit in bs:
  add(f'joint_{j}_{suffix}',f'Joint {j} {title}','Raw joint definition',f'Joint {j} (ordered standard-DH row {j}): {definition}',expr.replace('[j]',f'[{j-1}]').replace('j < jointCount',f'{j-1} < jointCount')+'; padded value0 when jointCount < '+str(j),unit,deps+["jointCount"] if 'jointCount' not in deps else deps,f'Applies specifically to the {j}th serial joint, not a robot or CSV row index. '+definition,limit+f' If fewer than {j} joints exist, this slot is exactly0; presence is classification variable#{9+(j-1)*10}.',BASE_SOURCE+':317–329 · ordered joint loop')
context=[
('initial_cartesian_error','Initial Cartesian distance','initialError if finite else0','m',['initialError'],'Distance between seed FK end point and requested target, imported from pre-solve metric.','Generated by DiagnosticRunMetricsCalculator.calculateInitialError: distance(FK(seed),target) when FK valid, otherwise NaN. Missing/nonfinite becomes0; pair with initial_error_available.'),
('initial_error_available','Initial-error availability','1 if initialError finite else0','indicator0/1',['initialError'],'Distinguishes observed initial distance from zero imputation.','1 only means a finite CSV value exists, not that target is reachable.'),
('seed_min_normalized_limit_margin','Smallest seed joint-limit margin','seedMinNormalizedLimitMargin if finite else0','dimensionless',['seedMinNormalizedLimitMargin'],'Stored minimum over joints of distance to the nearest limit divided by full joint span.','Generator computes min_j clamp(min((q-min)/span,(max-q)/span),0,0.5); invalid range/state yieldsNaN. Missing becomes0; inspect seed_margin_available.'),
('seed_margin_available','Seed-margin availability','1 if seedMinNormalizedLimitMargin finite else0','indicator0/1',['seedMinNormalizedLimitMargin'],'Finite-value availability of the stored seed-limit metric.','Does not distinguish exact boundary0 from missing0 without the paired value.'),
('seed_log_condition_number','Stored base-ten log seed condition','seedLogConditionNumber if finite else0','base10 log of dimensionless condition',['seedLogConditionNumber'],'Stored pre-solve condition compressed by decimal logarithm.','Generator uses log10(condition) clamped[0,policy cap], defaultcap12; +Infinity→cap, NaN/nonpositive→missing. This differs from natural-log recomputed expanded condition. Imported finite values are accepted as encoded.'),
('seed_condition_available','Seed-condition availability','1 if seedLogConditionNumber finite else0','indicator0/1',['seedLogConditionNumber'],'Marks whether the stored seed-condition logarithm was finite.','1 does not exclude capped infinite condition; the generator maps +Infinity to cap12.'),
('target_radius','Target distance from base origin','hypot(hypot(targetX,targetY),targetZ)','m',['targetX','targetY','targetZ'],'Euclidean norm of requested position in the base frame.','A radial scalar loses directional and orientation information.'),
('conservative_reach_bound','Conservative serial-chain extent sum','sum hypot(dhAMeters[j],if PRISMATIC max(abs(jointMinValues[j]),abs(jointMaxValues[j])) else abs(dhDMeters[j])); floor1e-12','m',['dhAMeters','dhDMeters','jointTypes','jointMinValues','jointMaxValues'],'Geometry-based outer extent bound using maximal prismatic travel magnitude.','An outer bound is not proof of workspace membership; angular and coupled constraints remain.'),
('target_reach_ratio','Target radius divided by extent bound','target_radius / conservative_reach_bound','dimensionless',['targetX','targetY','targetZ','dhAMeters','dhDMeters','jointTypes','jointMinValues','jointMaxValues'],'Radial target utilization of the conservative bound.','Ratios below1 do not certify reachability.'),
('prismatic_joint_fraction','Prismatic fraction','count(PRISMATIC)/jointCount','dimensionless',['jointTypes','jointCount'],'Fraction of present joints with translational coordinates.','Complement of revolute_joint_fraction for admitted types, not an independent DOF count.'),
('revolute_joint_fraction','Revolute fraction','(jointCount-count(PRISMATIC))/jointCount','dimensionless',['jointTypes','jointCount'],'Fraction of present joints with rotational coordinates.','Exact complement of prismatic_joint_fraction in this valid schema.'),
('mean_link_extent','Mean static DH extent','mean(hypot(dhAMeters[j],dhDMeters[j]))','m',['dhAMeters','dhDMeters'],'Average static per-row DH translation magnitude.','Prismatic active d is zero in stored DH, so this excludes current extension.'),
('link_extent_standard_deviation','Dispersion of static DH extents','population std(hypot(dhAMeters[j],dhDMeters[j]))','m',['dhAMeters','dhDMeters'],'Spread of static translation extents across the present chain.','Population denominatorN; zero with one joint. Prismatic extension is excluded.'),
('mean_joint_span','Mean raw joint coordinate span','mean(jointMaxValues-jointMinValues)','rad/m mixed when topology mixed',['jointMinValues','jointMaxValues'],'Mean of each raw maximum-minus-minimum span.','A mixed topology combines radians and metres; this numeric statistic has no single physical unit.'),
('minimum_joint_span','Smallest raw joint coordinate span','min(jointMaxValues-jointMinValues)','rad/m mixed when topology mixed',['jointMinValues','jointMaxValues'],'Smallest declared coordinate span in numeric internal units.','Comparing radians to metres in mixed chains is a model feature, not a physical dimensionless comparison.'),
('normalized_target_x','Reach-scaled target x','targetX / conservative_reach_bound','dimensionless',['targetX','dhAMeters','dhDMeters','jointTypes','jointMinValues','jointMaxValues'],'Target x expressed relative to conservative chain extent.','Bound denominatorfloored1e-12, not true directional workspace size.'),
('normalized_target_y','Reach-scaled target y','targetY / conservative_reach_bound','dimensionless',['targetY','dhAMeters','dhDMeters','jointTypes','jointMinValues','jointMaxValues'],'Target y expressed relative to conservative chain extent.','Bound denominatorfloored1e-12, not true directional workspace size.'),
('normalized_target_z','Reach-scaled target z','targetZ / conservative_reach_bound','dimensionless',['targetZ','dhAMeters','dhDMeters','jointTypes','jointMinValues','jointMaxValues'],'Target z expressed relative to conservative chain extent.','Bound denominatorfloored1e-12, not true directional workspace size.'),
('seed_home_offset_rms','RMS seed-to-home full-span offset','sqrt(mean(((seedJointValues-jointHomeValues)/(jointMaxValues-jointMinValues))^2))','dimensionless',['seedJointValues','jointHomeValues','jointMinValues','jointMaxValues'],'Root-mean-square seed displacement from home normalized by full spans.','Full-span denominator; expanded per-joint seed_home_delta_normalized uses half spans and is twice as large.'),
('seed_home_offset_mean_absolute','Mean absolute seed-to-home full-span offset','mean(abs((seedJointValues-jointHomeValues)/(jointMaxValues-jointMinValues)))','dimensionless',['seedJointValues','jointHomeValues','jointMinValues','jointMaxValues'],'Average unsigned displacement from home as a fraction of each complete allowed span.','Signs are discarded; does not measure travel during IK.'),
('seed_home_offset_max_absolute','Maximum seed-to-home full-span offset','max(abs((seedJointValues-jointHomeValues)/(jointMaxValues-jointMinValues)))','dimensionless',['seedJointValues','jointHomeValues','jointMinValues','jointMaxValues'],'Largest normalized initial displacement among present joints.','A maximum summarizes one joint and hides which joint produced it.'),
('workspace_boundary_proximity','Distance from unit radial reach ratio','abs(1-target_radius/conservative_reach_bound)','dimensionless',['targetX','targetY','targetZ','dhAMeters','dhDMeters','jointTypes','jointMinValues','jointMaxValues'],'Absolute separation from the conservative outer radial boundary in normalized coordinates.','Despite its name, this is a distance (smaller means nearer); ignores inner voids and full workspace geometry.')]
for id,title,expr,unit,deps,definition,limit in context:
 source=BASE_SOURCE+':332–387 · context block'
 if id in ['seed_log_condition_number','seed_min_normalized_limit_margin']:source+='; app/src/main/java/com/robotkinematicslab/mobile/diagnostics/metrics/DiagnosticDerivedMetricsCalculator.kt:58,155'
 if id=='initial_cartesian_error':source+='; app/src/main/java/com/robotkinematicslab/mobile/diagnostics/metrics/DiagnosticRunMetricsCalculator.kt:40'
 add(id,title,'Stored seed context' if len(entries)<114 else 'Initial geometric context',definition,expr,unit,deps,definition,limit,source)
# Explanations in exact GLOBAL_FEATURE_NAMES order.
emeaning=[
'Seed end-effector x coordinate from fresh standard-DH FK.','Seed end-effector y coordinate from fresh standard-DH FK.','Seed end-effector z coordinate from fresh standard-DH FK.',
'Seed FK x divided by conservative chain extent.','Seed FK y divided by conservative chain extent.','Seed FK z divided by conservative chain extent.',
'Signed x error from seed end point toward target.','Signed y error from seed end point toward target.','Signed z error from seed end point toward target.',
'Signed x seed error scaled by conservative reach.','Signed y seed error scaled by conservative reach.','Signed z seed error scaled by conservative reach.',
'Euclidean seed-to-target error recomputed from geometry, independently of the stored initialError column.','Fresh seed error divided by conservative chain extent.',
'Target cylindrical distance from the base z axis.','Cylindrical target radius divided by conservative extent.',
'Sine of target azimuth atan2(y,x).','Cosine of target azimuth atan2(y,x).','Sine of target elevation atan2(z,cylindrical radius).','Cosine of target elevation atan2(z,cylindrical radius).',
'Seed end-point distance from origin divided by conservative extent.','Signed difference between target and seed radial distances divided by conservative extent.',
'Largest singular value of the half-span-scaled positional Jacobian.','Middle of the three task-space singular values.','Third/smallest task-space singular value, including structural zero for fewer than3 DOFs.',
'Natural logarithm of the largest scaled Jacobian singular value.','Natural logarithm of the middle scaled singular value.','Natural logarithm of the third scaled singular value.',
'Capped ratio of largest to minimum expected-rank singular value.','Natural logarithm of recomputed condition, unlike the stored base-ten context logarithm.','Reciprocal of the recomputed expected-rank condition.',
'Product of the first min(jointCount,3) scaled singular values.','Natural log of that expected-rank singular-value product.','Sum of squared scaled singular values; total positional Jacobian energy.',
'Shannon entropy of normalized squared singular values.','Exponential spectral entropy as an effective-rank statistic.','Number of singular values above the relative/absolute threshold divided by3.',
'Minimum expected-rank singular value divided by maximum.','Middle singular value divided by maximum.',
'Square root of the error-direction quadratic form of scaled Jacobian Gram.','Directional mobility divided by the maximum scaled singular value.',
'Euclidean norm of un-clipped preview DLS raw joint-coordinate increments.','RMS of the raw preview joint increments.','Largest absolute raw preview joint increment.',
'Euclidean norm of DLS increments divided by each half span.','RMS of half-span-normalized DLS increments.','Maximum absolute half-span-normalized DLS increment.',
'Uniform scalar reducing the normalized DLS step to maxStep when necessary.','Norm of Cartesian displacement predicted by the linearly approximated clipped step.','Norm of residual predicted by the clipped linearized DLS step.',
'Predicted residual norm divided by initial error norm.','Fractional linearized error decrease after the clipped preview.','Cosine alignment of the initial error and predicted Cartesian step.',
'Preview damping chosen from configured damping, singularity thresholds and initial error.','Preview damping relative to the minimum expected-rank singular value.','Preview damping relative to the maximum singular value.',
'Normalized joint effort divided by initial Cartesian error.','Smallest available joint travel to boundary divided by proposed raw step magnitude.','Average available travel-to-raw-step ratio.',
'Fraction of raw DLS steps pointing toward the nearer joint limit.','Fraction of raw increments exceeding a complete half span in absolute normalized magnitude.',
'Mean nearest seed-to-limit margin normalized by full span.','Population standard deviation of nearest seed margins.','Smallest nearest seed margin.','Largest nearest seed margin.',
'Harmonic mean of nearest margins with a finite floor.','Mean negative-log barrier of nearest joint margins.','RMS of negative-log joint barriers.','Largest negative-log joint barrier.',
'Fraction of joints with nearest margin at most5% of full span.','Fraction of joints with nearest margin at most10% of full span.','Fraction of joints with nearest margin at most20% of full span.',
'Mean signed seed position relative to interval centre in half-span units.','RMS signed centre-relative seed positions.','Largest absolute half-span-normalized centre displacement.',
'Mean raw joint span recomputed from limits.','Population standard deviation of raw coordinate spans.','Largest raw coordinate span.','Ratio of largest to smallest raw span, with denominator floor.',
'Sum of static DH translation extents, floored for finite division.','Largest static DH translation extent.','Smallest static DH translation extent.','Population standard deviation of static DH extents.',
'Static extent standard deviation divided by mean.','Largest static extent as a fraction of their sum.','Shannon entropy of static extent shares.','Exponential extent-share entropy as effective link count.',
'Triangle-inequality inner radial void proxy based on the dominant static extent.','Inner radial proxy divided by conservative extent.',
'Signed target-radius distance above the inner proxy, normalized by extent.','Signed conservative outer-radius headroom above target radius.','Nearest absolute radial distance to the inner/outer proxies in extent units.',
'Sum of absolute standard-DH a parameters.','Sum of absolute static standard-DH d parameters.','Fraction of total absolute a+d contributed by a.',
'Mean sine of static DH twist angles.','Mean cosine of static DH twist angles.','Resultant length of mean twist sine/cosine: circular concentration.',
'Fraction of twists with absolute angle greater than1e-6 radians.','Count of joints beyond3 divided by actual count.','Joint count divided by the3 positional task coordinates.',
'Fraction of adjacent joints whose revolute/prismatic type changes.','Longest consecutive run of equal joint type divided by count.'
]
assert len(emeaning)==103,len(emeaning)
# Units explicitly audited for each mathematical block; logs are numeric-log transforms.
eunits=['m']*3+['dimensionless']*3+['m']*3+['dimensionless']*3+['m','dimensionless','m','dimensionless']+['dimensionless']*6
assert len(eunits)==22
eunits += ['m']*3+['ln of numeric metres']*3+['dimensionless','ln of dimensionless condition','dimensionless','m^min(jointCount,3)','ln of numeric manipulability','m²','nats','dimensionless','dimensionless','dimensionless','dimensionless']
eunits += ['m','dimensionless','rad/m mixed for mixed topology','rad/m mixed for mixed topology','rad/m mixed for mixed topology','dimensionless','dimensionless','dimensionless','dimensionless','m','m','dimensionless','dimensionless','dimensionless','m in the half-span-scaled preview','dimensionless','dimensionless','1/m','dimensionless','dimensionless','dimensionless','dimensionless']
eunits += ['dimensionless']*14+['rad/m mixed for mixed topology']*3+['numeric ratio; mixed rad/m spans when topology mixed']
eunits += ['m']*4+['dimensionless']*4+['m']+['dimensionless']*4+['m']*2+['dimensionless']*9
assert len(eunits)==103,len(eunits)
for i,(name,expr,meaning,unit) in enumerate(zip(enames,eexpr,emeaning,eunits)):
 family=('Seed task geometry' if i<22 else 'Scaled Jacobian spectrum' if i<39 else 'Directional DLS preview' if i<61 else 'Joint-limit pressure' if i<79 else 'Chain geometry')
 limit={'Seed task geometry':'All quantities refer to the initial seed, not final IK state. Angular sine/cosine returns0 when direction is undefined at radius<=1e-12.',
 'Scaled Jacobian spectrum':'Positional3xN Jacobian is scaled by joint half spans. Expected rank=min(N,3), observed rank uses max(1e-12,sigmaMax*1e-9). Zero Gram gives observed rank0 but entropy0 and effective rank1; this statistic is not algebraic rank. safeLog clamps[1e-12,1e12]; condition fallback1e12 is a convention, not a measured finite inverse.',
 'Directional DLS preview':'This is a single local linearized preview, not actual solver convergence or certified reachability. Half-span normalization removes rad/m mixture only for normalized steps. Damping and step clipping follow the documented preview, not a measured final update. Near-zero denominators use EPS1e-12; headroom is capped1e6.',
 'Joint-limit pressure':'Computed over present joints only, not padded slots. Margins use full spans; signed positions use half spans (floor1e-12). Log/harmonic margin floor1e-6 prevents infinity. Mixed raw spans combine metres and radians.',
 'Chain geometry':'These are static-chain descriptors or radial proxies, not a certified workspace, independence/rank claim or manufacturer specification. Static DH d excludes active prismatic displacement. Extent denominator floor1e-12; zero extents produce entropy0/effective count1 by convention.'}[family]
 if name=='dls_predicted_limit_violation_fraction':limit+=' Despite the name, |dq/halfSpan|>1 is counted; it does NOT test actual crossing from the current seed to a limit.'
 add(name,human(name),family,meaning,expr,unit,[],meaning,limit,EXP_SOURCE+':calculate · GLOBAL_FEATURE_NAMES['+str(i)+']',aliases=COMMON)
ejmeaning=[
('Seed position relative to centre','Signed displacement of the initial coordinate from the interval centre in half-span units.','-1 at lower limit,+1 at upper; zero at centre.'),
('Lower-limit margin','Initial coordinate minus minimum, divided by full allowed span.','0 at lower limit,1 at upper; not a distance in metres/radians.'),
('Upper-limit margin','Maximum minus initial coordinate, divided by full allowed span.','1 at lower limit,0 at upper; complementary to lower margin.'),
('Nearest-limit margin','Minimum of lower and upper normalized margins.','0 at either boundary,0.5 at centre; loses which side is nearer.'),
('Seed limit barrier','Negative natural log of nearest margin floored at1e-6.','Saturates at -ln(1e-6) at a boundary; a finite modelling barrier, not physical force.'),
('Home position relative to centre','Declared home offset from interval centre divided by half span.','Describes home, not current or solved state.'),
('Seed minus home in half-span units','Initial seed displacement from home divided by half span.','Twice the full-span offset used in context130 global seed-home statistics.'),
('Sine of revolute seed angle','Sine of initial coordinate for a revolute joint;0 for prismatic.','Prismatic0 is a type sentinel, not sine of a translation.'),
('Cosine of revolute seed angle','Cosine of initial coordinate for a revolute joint;0 for prismatic.','Both prismatic and missing slot return0, not cos(0)=1.'),
('Static extent divided by reach','hypot(a,d) for this static DH row divided by conservative extent.','Static prismatic d is0; current extension is absent.'),
('Active extent divided by reach','hypot(a,active d) at seed divided by conservative extent.','For prismatic active d is the seed translation; for revolute d remains static.'),
('Sine of DH twist','Sine of this row static alpha angle.','Dimensionless periodic encoding; absent slot returns0.'),
('Cosine of DH twist','Cosine of this row static alpha angle.','For a present zero twist returns1, while absent slot returns0.'),
('Sine of active DH theta','Sine of seed rotation for revolute or static theta for prismatic.','It does not apply trigonometry to a prismatic coordinate.'),
('Cosine of active DH theta','Cosine of seed rotation for revolute or static theta for prismatic.','Present zero angle yields1; all absent slots, including cosine, yield0.')]
for j in range(1,11):
 for suffix,expr,(title,meaning,limit) in zip(esuffix,ejexpr,ejmeaning):
  add(f'joint_{j}_{suffix}',f'Joint {j} {title.lower()}','Per-joint transforms',f'Ordered joint {j}: '+meaning,'index = '+str(j-1)+'; '+expr,'dimensionless',[],f'This input describes the {j}th joint only. '+meaning,limit+f' Present only when jointCount≥{j}; otherwise exactly0 in all15 expanded slots. Check baseline joint_{j}_present. Positive span required; half span floor1e-12.',EXP_SOURCE+':calculate · JOINT_FEATURE_SUFFIXES['+str(esuffix.index(suffix))+']',aliases=COMMON)
rmeaning=[
'Squared target-radius utilization of the seed-posture extent sum.',
'Fourth power of target-radius utilization, emphasizing extreme numeric radial utilization.',
'Squared normalized penetration inside the seed-posture triangle inner-void proxy.',
'Reciprocal normalized distance to the nearest inner/outer radial proxy, floored to remain finite.',
'Indicator that normalized radial distance to either proxy is at most0.05.',
'Indicator that normalized radial distance to either proxy is at most0.10.',
'Mean squared absolute centre-relative joint pressure.',
'Mean fourth-power centre-relative joint pressure.',
'RMS of squared pressure: square root of mean pressure fourth powers.',
'Population standard deviation of absolute joint pressures.',
'Fraction of joints whose nearest margin is at most0.02 of full span.',
'Fraction of joints whose nearest margin is at most0.05 of full span.',
'Squared share of the largest seed-posture active extent.',
'Squared fraction of seed-posture extent occupied by the inner-void proxy.',
'Gini inequality of seed-posture active extents over present joints.'
]
for name,expr,meaning in zip(rnames,rexpr,rmeaning):
 add(name,human(name),'Research v2 nonlinear global context',meaning,expr,'dimensionless',[],meaning,
  'Experimental pre-solve transform, not a proven improvement. Research reach uses current seed prismatic displacement, unlike the conservative bound of expanded383. Radial boundaries are proxies, not membership certificates. pressure=abs(centre offset/halfspan) clipped≤1; inverse margin floor1e-4, so reciprocal≤10000. No solver outcome, actual iteration count or final error is used.',RES_SOURCE+':calculate · GLOBAL_NAMES['+str(rnames.index(name))+']',aliases=RESEARCH)
rjmeaning=[
('Squared limit pressure','Square of absolute centre-relative position in half-span units.','Emphasizes high pressure regardless of lower/upper side.'),
('Fourth-power limit pressure','Fourth power of absolute centre-relative position.','Compresses central pressures more than square; not a calibrated failure probability.'),
('Inverse nearest margin','Reciprocal of nearest full-span margin floored at1e-4.','Finite cap10000; at centre value2, at boundary cap10000.'),
('Exponential limit pressure','exp(-10*nearest full-span margin).','1 at a boundary and exp(-5) at centre; not a physical energy or likelihood.'),
('Squared home displacement','Square of seed-home offset divided by half span, after clipping displacement to[-2,2].','Maximum4 under valid limits; direction is discarded.'),
('Home-displacement and limit-pressure interaction','Squared half-span home displacement multiplied by squared limit pressure.','Product represents co-occurrence for this joint; it is not causal interaction evidence.'),
('Active-extent and limit-pressure interaction','This joint active extent share multiplied by squared limit pressure.','Uses current prismatic extension in extent, not raw static d or maximal reach; no causal interpretation.')]
for j in range(1,11):
 for suffix,expr,(title,meaning,limit) in zip(rsuffix,rjexpr,rjmeaning):
  add(f'research_v2_joint_{j}_{suffix}',f'Joint {j} research {title.lower()}','Research v2 per-joint nonlinear context',f'Ordered joint {j}: '+meaning,'index = '+str(j-1)+'; '+expr,'dimensionless',[],f'Specific to joint {j} in the declared serial order. '+meaning,limit+f' If jointCount<{j}, all7 research slots for this joint are exactly0; interpret using joint_{j}_present. No features depend on solved coordinates. Margins and pressure derive from the same validated seed and limits.',RES_SOURCE+':calculate · JOINT_SUFFIXES['+str(rsuffix.index(suffix))+']',aliases=RESEARCH)
# Stored condition fields are not independently recomputed before solving: capture depends on execution.
for card in entries:
 if card['technicalId'] in ('seed_log_condition_number','seed_condition_available'):
  card['origin']='Imported scientific CSV seedLogConditionNumber, derived from solverDiagnostics.seedConditionNumber. This recorded diagnostic is populated only if solver execution reaches Jacobian/condition analysis; early convergence can leave it NaN.'
  card['limitations'] += ' ADMISSIBILITY WARNING: availability depends on solver execution path, not solely on a guaranteed independent pre-solve computation. Early success before Jacobian analysis can yield availability0; therefore this flag can correlate with the outcome through control flow. Do not treat the stored value/flag as automatically safe prospective predictors. Compare with independently recomputed expanded Jacobian features under the intended prediction protocol.'
  card['possibleUse']='Audit diagnostic availability and predictor admissibility for the intended task. Any classification benefit must be checked for execution-path leakage; compare with a fresh pre-solve condition computation rather than assuming the recorded field is available at prediction time.'
  card['implementationSource'] += '; app/src/main/java/com/robotkinematicslab/mobile/solver/ik/InverseKinematicsSolver.kt:229,293,386'
assert len(entries)==468,len(entries)
assert len({e['technicalId'] for e in entries})==468
# Emit metadata in small initializer functions: no JVM method approaches the64KiB limit.
def k(s):return json.dumps(s,ensure_ascii=False).replace('$','\\$')
def record(e):
 vals=[str(e['index'])]+[k(e[f]) for f in ['technicalId','humanName','family','definition','origin','calculation','units']]+['listOf('+','.join(k(x) for x in e['dependencies'])+')']+[k(e[f]) for f in ['meaning','possibleUse','limitations','implementationSource','formulaVersion']]
 return 'FeatureDefinition('+',\n            '.join(vals)+')'
header='''package com.robotkinematicslab.mobile.ml.data

/** Encoder metadata used by the application feature registry. */
internal object FeatureDefinitionData {
    val classification:List<FeatureDefinition> by lazy {
        buildList {
'''
for i in range(0,468,12):header+=f'            addAll(block{i//12}())\n'
header+='''        }.also { FeatureDefinitionIntegrity.validate(ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.CONTEXT_RESEARCH_V2),it) }
    }
    fun forEntry(entry:RegisteredFeatureSet):List<FeatureDefinition> {
        val byName=classification.associateBy { it.technicalId }
        return entry.featureNames.mapIndexed { offset,id ->
            val source=requireNotNull(byName[id]) { "No verified dictionary card for $id; review the changed encoder." }
            if(entry.domain==FeatureSetDomain.CLASSIFICATION) source.copy(index=offset+1)
            else source.copy(index=offset+1,
                origin="Verified IK encoder receives validated RobotDefinition, RobotState, target and IKConfig; no22-column stored classification-context block. The same ID can have a different local index or logarithm floor. Dependency column names below identify equivalent scientific CSV inputs, not direct columns consumed by this live encoder.",
                calculation=when(id) {
                    "log_ik_tolerance" -> "ln(config.tolerance); positive finite input, no1e-12 floor. Float conversion must remain finite."
                    "log_ik_damping" -> "ln(config.damping); positive finite input, no1e-12 floor. Float conversion must remain finite."
                    "log_ik_max_step" -> "ln(config.maxStep); positive finite input, no1e-12 floor. Float conversion must remain finite."
                    else -> source.calculation
                },
                limitations="IK LOCAL INDEX ${offset+1}; classification index ${source.index} is a different namespace. " +
                    if(id in setOf("log_ik_tolerance","log_ik_damping","log_ik_max_step")) "Unlike classification, this encoder applies natural log without flooring. IK model normalization is separate. Positive finite controls and finite Float outputs required."
                    else source.limitations.replace("Classifier training normalization is a separate train-fitted operation, clipped to [-8,8], not this feature formula.","Verified IK normalization is separate from this feature formula."),
                implementationSource="app/src/main/java/com/robotkinematicslab/mobile/ml/ik/OneMicronIkFeatureEncoder.kt:encode; shared formula: " + source.implementationSource)
        }.also { FeatureDefinitionIntegrity.validate(entry.featureNames,it) }
    }
'''
for i in range(0,468,12):header+=f'    private fun block{i//12}():List<FeatureDefinition> = listOf(\n        '+',\n        '.join(record(e) for e in entries[i:i+12])+'\n    )\n'
header+='}\n'
(D/'FeatureDefinitionData.kt').write_text(header)
# Export the feature metadata used by the application.
(ROOT/'docs/FEATURE_DICTIONARY_468.json').write_text(json.dumps({'version':'feature-dictionary-v1','namespace':'classification','cards':entries},indent=2,ensure_ascii=False)+'\n')
files=['ml/data/ScientificDatasetTrainingReader.kt','ml/data/ExpandedContextFeatureCalculator.kt','ml/data/ResearchContextFeatureCalculator.kt','ml/ik/OneMicronIkFeatureEncoder.kt','diagnostics/metrics/DiagnosticDerivedMetricsCalculator.kt','diagnostics/metrics/DiagnosticRunMetricsCalculator.kt','diagnostics/metrics/DiagnosticMetricPolicy.kt','solver/ik/InverseKinematicsSolver.kt']
hashes={f:hashlib.sha256((P/f).read_bytes()).hexdigest() for f in files}
(ROOT/'docs/FEATURE_DICTIONARY_SOURCE_FINGERPRINTS.json').write_text(json.dumps(hashes,indent=2)+'\n')
print('wrote',len(entries),'cards',len(header),'Kotlin chars')

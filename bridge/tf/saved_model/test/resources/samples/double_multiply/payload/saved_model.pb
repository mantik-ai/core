½"
Á
x
Assign
ref"T

value"T

output_ref"T"	
Ttype"
validate_shapebool("
use_lockingbool(
8
Const
output"dtype"
valuetensor"
dtypetype
.
Identity

input"T
output"T"	
Ttype
e
MergeV2Checkpoints
checkpoint_prefixes
destination_prefix"
delete_old_dirsbool(
=
Mul
x"T
y"T
z"T"
Ttype:
2	

NoOp
M
Pack
values"T*N
output"T"
Nint(0"	
Ttype"
axisint 
C
Placeholder
output"dtype"
dtypetype"
shapeshape:
o
	RestoreV2

prefix
tensor_names
shape_and_slices
tensors2dtypes"
dtypes
list(type)(0
l
SaveV2

prefix
tensor_names
shape_and_slices
tensors2dtypes"
dtypes
list(type)(0
H
ShardedFilename
basename	
shard

num_shards
filename
N

StringJoin
inputs*N

output"
Nint(0"
	separatorstring 
s

VariableV2
ref"dtype"
shapeshape"
dtypetype"
	containerstring "
shared_namestring "serve*1.12.02v1.12.0-rc2-3-ga6d8ffae09¹
L
PlaceholderPlaceholder*
_output_shapes
: *
shape: *
dtype0
N
zerosConst*
valueB 2        *
dtype0*
_output_shapes
: 
l
Variable
VariableV2*
dtype0*
	container *
_output_shapes
: *
shape: *
shared_name 

Variable/AssignAssignVariablezeros*
use_locking(*
T0*
_class
loc:@Variable*
validate_shape(*
_output_shapes
: 
a
Variable/readIdentityVariable*
T0*
_class
loc:@Variable*
_output_shapes
: 
G
MulMulPlaceholderVariable/read*
T0*
_output_shapes
: 

initNoOp^Variable/Assign
U
Assign/valueConst*
valueB 2       @*
dtype0*
_output_shapes
: 

AssignAssignVariableAssign/value*
use_locking( *
T0*
_class
loc:@Variable*
validate_shape(*
_output_shapes
: 
P

save/ConstConst*
valueB Bmodel*
dtype0*
_output_shapes
: 

save/StringJoin/inputs_1Const*
_output_shapes
: *<
value3B1 B+_temp_0dc435fb32c0412e8aa487a49624c97e/part*
dtype0
u
save/StringJoin
StringJoin
save/Constsave/StringJoin/inputs_1*
	separator *
N*
_output_shapes
: 
Q
save/num_shardsConst*
value	B :*
dtype0*
_output_shapes
: 
\
save/ShardedFilename/shardConst*
value	B : *
dtype0*
_output_shapes
: 
}
save/ShardedFilenameShardedFilenamesave/StringJoinsave/ShardedFilename/shardsave/num_shards*
_output_shapes
: 
i
save/SaveV2/tensor_namesConst*
valueBBVariable*
dtype0*
_output_shapes
:
e
save/SaveV2/shape_and_slicesConst*
_output_shapes
:*
valueB
B *
dtype0
~
save/SaveV2SaveV2save/ShardedFilenamesave/SaveV2/tensor_namessave/SaveV2/shape_and_slicesVariable*
dtypes
2

save/control_dependencyIdentitysave/ShardedFilename^save/SaveV2*'
_class
loc:@save/ShardedFilename*
_output_shapes
: *
T0

+save/MergeV2Checkpoints/checkpoint_prefixesPacksave/ShardedFilename^save/control_dependency*
_output_shapes
:*
T0*

axis *
N
}
save/MergeV2CheckpointsMergeV2Checkpoints+save/MergeV2Checkpoints/checkpoint_prefixes
save/Const*
delete_old_dirs(
z
save/IdentityIdentity
save/Const^save/MergeV2Checkpoints^save/control_dependency*
_output_shapes
: *
T0
l
save/RestoreV2/tensor_namesConst*
valueBBVariable*
dtype0*
_output_shapes
:
h
save/RestoreV2/shape_and_slicesConst*
_output_shapes
:*
valueB
B *
dtype0

save/RestoreV2	RestoreV2
save/Constsave/RestoreV2/tensor_namessave/RestoreV2/shape_and_slices*
_output_shapes
:*
dtypes
2

save/AssignAssignVariablesave/RestoreV2*
validate_shape(*
_output_shapes
: *
use_locking(*
T0*
_class
loc:@Variable
(
save/restore_shardNoOp^save/Assign
-
save/restore_allNoOp^save/restore_shard "<
save/Const:0save/Identity:0save/restore_all (5 @F8"J
	variables=;
9

Variable:0Variable/AssignVariable/read:02zeros:08"T
trainable_variables=;
9

Variable:0Variable/AssignVariable/read:02zeros:08*[
serving_defaultH

x
Placeholder:0 
y
Mul:0 tensorflow/serving/predict
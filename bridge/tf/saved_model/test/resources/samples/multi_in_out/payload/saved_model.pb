?
??
:
Add
x"T
y"T
z"T"
Ttype:
2	
8
Const
output"dtype"
valuetensor"
dtypetype
?
Max

input"T
reduction_indices"Tidx
output"T"
	keep_dimsbool( " 
Ttype:
2	"
Tidxtype0:
2	

NoOp
C
Placeholder
output"dtype"
dtypetype"
shapeshape:"serve*1.12.02v1.12.0-rc2-3-ga6d8ffae09?
n
PlaceholderPlaceholder*
dtype0	*'
_output_shapes
:?????????*
shape:?????????
p
Placeholder_1Placeholder*
dtype0	*'
_output_shapes
:?????????*
shape:?????????
X
AddAddPlaceholderPlaceholder_1*
T0	*'
_output_shapes
:?????????
W
Max/reduction_indicesConst*
dtype0*
_output_shapes
: *
value	B :
q
MaxMaxAddMax/reduction_indices*
T0	*#
_output_shapes
:?????????*

Tidx0*
	keep_dims( 

initNoOp "*?
serving_default?
)
a$
Placeholder:0	?????????
+
b&
Placeholder_1:0	?????????!
x
Add:0	?????????
y
Max:0	?????????tensorflow/serving/predict
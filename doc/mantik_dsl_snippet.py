# Define a custom type
# Defining the first input type could be a sanity check for the whole pipeline,
# especially if data is taken from the web
special_type = types.from_json(
    """
    ...
    """
)

# Initialize tasks as instance of `dsl.MantikItem`, pass additional configuration
get_data = MantikItem(name="mantikai/binary:v1").configure(
    params={"file": "<filename>"}, input_type=special_type
)
# Define shorthand for output names, valid globally
images, labels = get_data.get_output_reference()

pre_process = MantikItem(name="thomas/image-processor")

# mark `alpha` as hyperparameter, to be used below
alpha = HyperParameter()
train = MantikItem(name="mantikai/sklearn_simple:v1").configure(
    params={"model_type": "GradientBoosting", "learning_rate": alpha}
)

# Common tasks should be builtins, especially evaluation, SQL like operations, timelag
evaluate = evaluate(metric="rmse")

# Aggregate multiple steps so that both can be used in hyperparameter search
train_and_evaluate = pipeline_component(train, evaluate)

# Set input names so that they can be referenced easily in the pipeline definition
train_and_evaluate.train.set_input_name("data_train")
train_and_evaluate.evaluate.set_input_name("data_test")

# Define hyperparameter search; can be more complicated
hyperparameter_search = HyperParameterSearch(
    train_and_evaluate,
    hyperparameters={"alpha": range(10)},
    metric=train_and_evaluate.evaluate.metric,
)

# Select best model, is a runtime placeholder here
BEST_MODEL = hyperparameter_search.output.models.best

deploy = save_model()
MODEL_ID = Placeholder(deploy.output.model_id)

# Inputs are set in the pipeline so that Items can be reused in multiple pipelines
# Stages are for now only a helper for grouping operations. They will be helpful in the UI and as breaking points for debugging and importing pipeline parts
# Configuration can also be done in the pipeline definition; this might come in handy for more complex pipelines with repeated use of mantik items
batch_pipeline = MantikPipeline(
    stages={
        "stage1": [
            get_data,
            pre_process.set_input(images),
            dsl.builtins.join(
                inputs=[pre_process.output, labels], reference="join1", how="inner"
            ),  # reference arg is for referencing this particular join in later pipeline steps; join has pandas like arguments
        ],
        "stage2": [
            dsl.builtins.train_test_split(data=[join1.output], reference="split"),
            hyperparameter_search.set_input(
                data_train=split.output.train, data_test=split.output.test
            ),
        ],
        "stage3": [deploy.set_input(BEST_MODEL)],
    }
)

predict = makeMantikItem(load_model(MODEL_ID).predictor) # Load model and use as MantikItem 

predict_pipeline = MantikPipeline(
    input_features,  # Pipelines can have an input argument (?)
    stages={
        "stage1": [input_features, pre_process.set_input(prediction_input.output)],
        "stage2": [predict.set_input(pre_process.output)],
    },
    return_value=predict.output.value,  # Pipeline can return predicted value
)

##########################################################

# Time lag
# Suppose get_data returns a timeline; can be imported from somewhere else

dummy_pipeline = MantikPipeline(
    stages={
        "stage1": [
            get_timeline,
            dsl.builtins.timelag(
                timeline=get_data.output,
                lag_column="time",
                lag="1h",
                reference="timelag1",
            ),
        ]
    }
)   

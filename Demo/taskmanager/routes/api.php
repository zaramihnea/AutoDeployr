<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;
use Illuminate\Support\Facades\DB;

/*
|--------------------------------------------------------------------------
| API Routes  
|--------------------------------------------------------------------------
*/

// Add new task endpoint - connects to Supabase PostgreSQL  
Route::post('/tasks', function (Request $request) {
    try {
        // Validate required fields
        if (!$request->has('title') || empty($request->title)) {
            return response()->json([
                'error' => 'Title is required'
            ], 400);
        }

        $title = $request->title;
        $description = $request->description ?? null;

        // Insert task into Supabase database
        $taskId = DB::table('tasks')->insertGetId([
            'title' => $title,
            'description' => $description,
            'completed' => false,
            'created_at' => now(),
            'updated_at' => now()
        ]);

        return response()->json([
            'id' => $taskId,
            'title' => $title,
            'description' => $description,
            'completed' => false,
            'message' => 'Task added successfully'
        ], 201);

    } catch (\Exception $e) {
        return response()->json([
            'error' => 'Failed to add task: ' . $e->getMessage()
        ], 500);
    }
});

// Get all tasks endpoint
Route::get('/tasks', function (Request $request) {
    try {
        $tasks = DB::table('tasks')
            ->orderBy('created_at', 'desc')
            ->get();

        return response()->json([
            'tasks' => $tasks,
            'count' => $tasks->count(),
            'message' => 'Tasks retrieved successfully'
        ], 200);

    } catch (\Exception $e) {
        return response()->json([
            'error' => 'Failed to retrieve tasks: ' . $e->getMessage()
        ], 500);
    }
});

// Complete task endpoint - POST /tasks/complete?id=123
Route::post('/tasks/complete', function (Request $request) {
    try {
        $taskId = $request->input('id'); // Changed from query() to input()
        
        if (!$taskId) {
            return response()->json([
                'error' => 'Task ID is required as query parameter (?id=123)'
            ], 400);
        }

        $updated = DB::table('tasks')
            ->where('id', $taskId)
            ->update([
                'completed' => true,
                'updated_at' => now()
            ]);

        if ($updated === 0) {
            return response()->json([
                'error' => 'Task not found'
            ], 404);
        }

        // Get the updated task
        $task = DB::table('tasks')->where('id', $taskId)->first();

        return response()->json([
            'id' => $task->id,
            'title' => $task->title,
            'description' => $task->description,
            'completed' => $task->completed,
            'message' => 'Task completed successfully'
        ], 200);

    } catch (\Exception $e) {
        return response()->json([
            'error' => 'Failed to complete task: ' . $e->getMessage()
        ], 500);
    }
});

// Delete task endpoint - DELETE /tasks?id=123
Route::delete('/tasks', function (Request $request) {
    try {
        $taskId = $request->input('id'); // Changed from query() to input()
        
        if (!$taskId) {
            return response()->json([
                'error' => 'Task ID is required as query parameter (?id=123)'
            ], 400);
        }

        // Get task before deletion for response
        $task = DB::table('tasks')->where('id', $taskId)->first();
        
        if (!$task) {
            return response()->json([
                'error' => 'Task not found'
            ], 404);
        }

        $deleted = DB::table('tasks')->where('id', $taskId)->delete();

        return response()->json([
            'id' => $task->id,
            'title' => $task->title,
            'message' => 'Task deleted successfully'
        ], 200);

    } catch (\Exception $e) {
        return response()->json([
            'error' => 'Failed to delete task: ' . $e->getMessage()
        ], 500);
    }
});

// Modify task description endpoint - PUT /tasks?id=123
Route::put('/tasks', function (Request $request) {
    try {
        $taskId = $request->input('id'); // Changed from query() to input()
        
        if (!$taskId) {
            return response()->json([
                'error' => 'Task ID is required as query parameter (?id=123)'
            ], 400);
        }

        if (!$request->has('description')) {
            return response()->json([
                'error' => 'Description is required in request body'
            ], 400);
        }

        $description = $request->description;

        $updated = DB::table('tasks')
            ->where('id', $taskId)
            ->update([
                'description' => $description,
                'updated_at' => now()
            ]);

        if ($updated === 0) {
            return response()->json([
                'error' => 'Task not found'
            ], 404);
        }

        // Get the updated task
        $task = DB::table('tasks')->where('id', $taskId)->first();

        return response()->json([
            'id' => $task->id,
            'title' => $task->title,
            'description' => $task->description,
            'completed' => $task->completed,
            'message' => 'Task description updated successfully'
        ], 200);

    } catch (\Exception $e) {
        return response()->json([
            'error' => 'Failed to update task: ' . $e->getMessage()
        ], 500);
    }
}); 
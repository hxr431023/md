#ifndef __CONTROLLER_H
#define __CONTROLLER_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

int cmd_exec(graal_isolatethread_t*, char*, char*, int);

int cmd_help(graal_isolatethread_t*, char*, int);

int cmd_write_idp(graal_isolatethread_t*, char*, char*, char*);

int cmd_to_idp(graal_isolatethread_t*, char*, char*, char*, int);

int cmd_read_idp(graal_isolatethread_t*, char*, char*, int);

int compress_file(graal_isolatethread_t*, char*, char*);

int compress_decompress_file(graal_isolatethread_t*, char*, char*);

int compress_string_to_file(graal_isolatethread_t*, char*, char*);

int compress_file_to_string(graal_isolatethread_t*, char*, char*, int);

int compress_ratio(graal_isolatethread_t*, char*);

int config_init(graal_isolatethread_t*, char*);

int config_load_settings(graal_isolatethread_t*);

int config_save_settings(graal_isolatethread_t*);

int config_get_setting(graal_isolatethread_t*, char*, char*, int);

int config_set_setting(graal_isolatethread_t*, char*, char*);

int config_has_setting(graal_isolatethread_t*, char*);

int config_remove_setting(graal_isolatethread_t*, char*);

int config_get_thread_mode(graal_isolatethread_t*, char*, int);

int config_set_thread_mode(graal_isolatethread_t*, char*);

int app_init(graal_isolatethread_t*, char*);

void app_shutdown(graal_isolatethread_t*);

int app_version(graal_isolatethread_t*, char*, int);

int app_new_file(graal_isolatethread_t*);

int app_open_file(graal_isolatethread_t*, char*);

int app_save_file(graal_isolatethread_t*);

int app_save_file_as(graal_isolatethread_t*, char*);

int app_get_content(graal_isolatethread_t*, char*, int);

int app_get_file_path(graal_isolatethread_t*, char*, int);

int app_is_dirty(graal_isolatethread_t*);

int app_insert_text(graal_isolatethread_t*, int, char*);

int app_delete_text(graal_isolatethread_t*, int, int);

int app_undo(graal_isolatethread_t*);

int app_redo(graal_isolatethread_t*);

int app_can_undo(graal_isolatethread_t*);

int app_can_redo(graal_isolatethread_t*);

void app_set_cursor(graal_isolatethread_t*, int);

int app_get_cursor(graal_isolatethread_t*);

int app_get_cursor_line(graal_isolatethread_t*);

int app_get_cursor_col(graal_isolatethread_t*);

int app_get_render_instructions(graal_isolatethread_t*, char*, int);

int app_read_render_cache(graal_isolatethread_t*, char*, int);

int app_render_html(graal_isolatethread_t*, char*, char*, int);

int app_get_status(graal_isolatethread_t*, char*, int);

int app_get_setting(graal_isolatethread_t*, char*, char*, int);

int app_set_setting(graal_isolatethread_t*, char*, char*);

int app_save_settings(graal_isolatethread_t*);

void app_log(graal_isolatethread_t*, char*, char*, char*);

int app_easter_exec(graal_isolatethread_t*, char*, char*, int);

int app_easter_detect(graal_isolatethread_t*, char*);

int app_write_idp(graal_isolatethread_t*, char*, char*, char*);

int app_to_idp(graal_isolatethread_t*, char*, char*, char*, int);

int app_read_idp(graal_isolatethread_t*, char*, char*, int);

int app_idp_process(graal_isolatethread_t*);

int convert_html(graal_isolatethread_t*, char*, char*, int);

int convert_html_file(graal_isolatethread_t*, char*, char*);

int convert_text(graal_isolatethread_t*, char*, char*, int);

int convert_text_file(graal_isolatethread_t*, char*, char*);

int convert_txt2md(graal_isolatethread_t*, char*, char*, int);

int convert_txt2md_file(graal_isolatethread_t*, char*, char*);

int doc_init(graal_isolatethread_t*, char*);

int doc_new_document(graal_isolatethread_t*);

int doc_open_document(graal_isolatethread_t*, char*);

int doc_save_document(graal_isolatethread_t*);

int doc_save_document_as(graal_isolatethread_t*, char*);

int doc_get_content(graal_isolatethread_t*, char*, int);

int doc_set_content(graal_isolatethread_t*, char*);

int doc_get_file_path(graal_isolatethread_t*, char*, int);

int doc_is_dirty(graal_isolatethread_t*);

void doc_set_cursor(graal_isolatethread_t*, int);

int doc_get_cursor(graal_isolatethread_t*);

int doc_get_cursor_line(graal_isolatethread_t*);

int doc_get_cursor_col(graal_isolatethread_t*);

int doc_insert_text(graal_isolatethread_t*, int, char*);

int doc_delete_text(graal_isolatethread_t*, int, int);

int doc_get_text(graal_isolatethread_t*, int, int, char*, int);

int doc_undo(graal_isolatethread_t*);

int doc_redo(graal_isolatethread_t*);

int doc_can_undo(graal_isolatethread_t*);

int doc_can_redo(graal_isolatethread_t*);

int doc_get_char_count(graal_isolatethread_t*);

int doc_get_word_count(graal_isolatethread_t*);

int doc_get_line_count(graal_isolatethread_t*);

int file_read(graal_isolatethread_t*, char*, char*, int);

int file_write(graal_isolatethread_t*, char*, char*);

int file_append(graal_isolatethread_t*, char*, char*);

int file_exists(graal_isolatethread_t*, char*);

long long int file_size(graal_isolatethread_t*, char*);

int file_delete(graal_isolatethread_t*, char*);

int file_name(graal_isolatethread_t*, char*, char*, int);

int file_extension(graal_isolatethread_t*, char*, char*, int);

int idp_push(graal_isolatethread_t*, char*, char*, char*);

int idp_pop_json(graal_isolatethread_t*, char*, int);

int idp_pop_idp(graal_isolatethread_t*, char*, int);

int idp_peek_json(graal_isolatethread_t*, char*, int);

int idp_stack_size(graal_isolatethread_t*);

int idp_stack_clear(graal_isolatethread_t*);

int lerf_load(graal_isolatethread_t*, char*);

int lerf_get(graal_isolatethread_t*, char*, char*, int);

int lerf_get_from_file(graal_isolatethread_t*, char*, char*, char*, int);

int lerf_get_multi(graal_isolatethread_t*, char*, char*, int);

int lerf_list_all(graal_isolatethread_t*, char*, char*, int);

int lerf_validate(graal_isolatethread_t*, char*);

int lerf_key_count(graal_isolatethread_t*, char*);

int log_init(graal_isolatethread_t*, char*);

void log_shutdown(graal_isolatethread_t*);

void log_write(graal_isolatethread_t*, char*, char*, char*);

void log_write_end(graal_isolatethread_t*, char*, char*, char*);

void log_set_enabled(graal_isolatethread_t*, char*, int);

int log_is_enabled(graal_isolatethread_t*, char*);

int log_clear_all(graal_isolatethread_t*);

long long int log_size(graal_isolatethread_t*);

int log_cache_lookup(graal_isolatethread_t*, char*, char*, char*, int);

int log_cache_put(graal_isolatethread_t*, char*, char*, char*);

int render_html(graal_isolatethread_t*, char*, char*, int);

int classify_line(graal_isolatethread_t*, char*);

int count_inline_styles(graal_isolatethread_t*, char*);

int count_lines(graal_isolatethread_t*, char*);

int count_words(graal_isolatethread_t*, char*);

int count_chars(graal_isolatethread_t*, char*);

int markdown_write_idp(graal_isolatethread_t*, char*, char*, char*);

int markdown_to_idp(graal_isolatethread_t*, char*, char*, char*, int);

int markdown_read_idp(graal_isolatethread_t*, char*, char*, int);

int quicksort_ints(graal_isolatethread_t*, char*, char*, char*, int);

int quicksort_strings(graal_isolatethread_t*, char*, char*, char*, int);

int quicksort_json_by_field(graal_isolatethread_t*, char*, char*, char*, char*, int);

int quicksort_info(graal_isolatethread_t*, char*, int);

int render_ui(graal_isolatethread_t*, char*, char*, int);

int render_line(graal_isolatethread_t*, char*, char*, int);

int render_line_count(graal_isolatethread_t*, char*);

int render_get_line(graal_isolatethread_t*, char*, int, char*, int);

int render_find(graal_isolatethread_t*, char*, char*, char*, int);

int render_replace(graal_isolatethread_t*, char*, char*, char*, char*, int);

int render_insert(graal_isolatethread_t*, char*, char*, char*, char*, char*, int);

int render_paged(graal_isolatethread_t*, char*, int, int, char*, int);

int render_write_idp(graal_isolatethread_t*, char*, char*, char*);

int render_to_idp(graal_isolatethread_t*, char*, char*, char*, int);

int render_read_idp(graal_isolatethread_t*, char*, char*, int);

int single_try_lock(graal_isolatethread_t*, char*);

void single_release(graal_isolatethread_t*);

int single_is_locked(graal_isolatethread_t*);

int single_lock_path(graal_isolatethread_t*, char*, int);

int threadstat_init(graal_isolatethread_t*, char*);

int threadstat_get_mode(graal_isolatethread_t*, char*, int);

int threadstat_set_mode(graal_isolatethread_t*, char*);

int threadstat_register(graal_isolatethread_t*, char*, char*);

int threadstat_unregister(graal_isolatethread_t*, char*);

int threadstat_sample(graal_isolatethread_t*);

int threadstat_get_json(graal_isolatethread_t*, char*, int);

int threadstat_memory(graal_isolatethread_t*, char*, int);

void threadstat_disk_read(graal_isolatethread_t*, long long int);

void threadstat_disk_write(graal_isolatethread_t*, long long int);

int threadstat_disk(graal_isolatethread_t*, char*, int);

int tmmf_read_all(graal_isolatethread_t*, char*, char*, int);

int tmmf_read_tag(graal_isolatethread_t*, char*, char*, char*, int);

int tmmf_search(graal_isolatethread_t*, char*, char*, char*, int);

int tmmf_list_tags(graal_isolatethread_t*, char*, char*, int);

int tmmf_line_count(graal_isolatethread_t*, char*);

int tmmf_write_tag(graal_isolatethread_t*, char*, char*, char*);

int visualize_stats_to_file(graal_isolatethread_t*, char*, char*);

int visualize_stats_to_html(graal_isolatethread_t*, char*, char*, int);

int visualize_stats_sorted_html(graal_isolatethread_t*, char*, char*, char*, char*, int);

int visualize_thread_modes(graal_isolatethread_t*, char*, int);

#if defined(__cplusplus)
}
#endif
#endif

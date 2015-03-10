$(function() {
	'use strict';

	$('.carousel').carousel();
	$('.carousel').on('slid.bs.carousel', function() {
		var n_slide = $(this).find('.active').attr('data-slide');;
		var new_slide_c = '#slide-control-' + n_slide;
		
		$('#slide-control .active').removeClass('active');
		$(new_slide_c).addClass('active');
	});

	$('#slide-control .slide-control-item').hover(function() {
		/* change to the specific slide */
		var n_slide = $(this).attr('data-slide-to');

		$('.carousel').carousel('pause');
		$('.carousel').carousel(parseInt(n_slide))
	}, function() {
		/* restart carousel */
		$('.carousel').carousel('cycle');
	});

	$(document).scroll(function() {
		/* Fix navbar and add search when scroll 180px */
		var HEIGHT = 180;
		var scrollHeight = $(window).scrollTop();
		var searchForm = '<form id="search-form" class="navbar-form navbar-right" role="search" method="GET" action="/search/">\
							<div class="form-group"><div class="input-group">\
          					<input type="text" class="form-control" name="query" placeholder="搜索">\
          	    			<div class="input-group-addon">\
	      					<img src="/media/search_icon.png" alt="search icon"/>\
	    					</div></div></div>\
						  	</form>'

		if (scrollHeight > HEIGHT) {
			$('#category').addClass('navbar-fixed-top')
						  .removeClass('navbar-inverse')
						  .addClass('navbar-default');
			if ($('#search-form').length == 0) {
				$('#category-bar').append(searchForm);
			}
		} else {
			$('#category').removeClass('navbar-fixed-top')
						  .removeClass('navbar-default')
						  .addClass('navbar-inverse');
			$('#search-form').remove();
		}

	});
})
